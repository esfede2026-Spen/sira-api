package com.infosoft.sira.academico;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/personas/{personaId}/academico")
public class AcademicoController {
    private final JdbcTemplate db;
    private final ObjectMapper mapper = new ObjectMapper();

    public AcademicoController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable long personaId) {
        var r = db.queryForList("select * from sira.per_persona_datos_academicos where id_persona=?", personaId);
        if (r.isEmpty()) return Map.of("estudios", List.of());
        Map<String, Object> m = new HashMap<>(r.get(0));
        String obs = t(m.get("observacion"));
        List<Map<String, Object>> estudios = parseEstudios(obs, m);
        enrichEstudios(estudios);
        m.put("estudios", estudios);
        m.put("observacion", cleanObservacion(obs));
        return m;
    }

    @PostMapping("/estudios")
    @Transactional
    public Map<String, Object> agregarEstudio(@PathVariable long personaId, @RequestBody Map<String, Object> nuevo) {
        var r = db.queryForList("select * from sira.per_persona_datos_academicos where id_persona=?", personaId);
        Map<String, Object> m = r.isEmpty() ? new HashMap<>() : new HashMap<>(r.get(0));
        String obs = t(m.get("observacion"));
        List<Map<String, Object>> estudios = parseEstudios(obs, m);
        Map<String, Object> est = new HashMap<>();
        est.put("tituloId", n(nuevo.get("tituloId") != null ? nuevo.get("tituloId") : nuevo.get("titulo1Id")));
        est.put("especialidadId", n(nuevo.get("especialidadId")));
        est.put("institucion", t(nuevo.get("institucion")));
        est.put("anioGraduacion", t(nuevo.get("anioGraduacion")));
        estudios.add(est);
        saveEstudios(personaId, m, estudios, cleanObservacion(obs));
        enrichEstudios(estudios);
        return Map.of("guardado", true, "estudios", estudios);
    }

    @DeleteMapping("/estudios/{idx}")
    @Transactional
    public Map<String, Object> eliminarEstudio(@PathVariable long personaId, @PathVariable int idx) {
        var r = db.queryForList("select * from sira.per_persona_datos_academicos where id_persona=?", personaId);
        if (!r.isEmpty()) {
            Map<String, Object> m = new HashMap<>(r.get(0));
            String obs = t(m.get("observacion"));
            List<Map<String, Object>> estudios = parseEstudios(obs, m);
            if (idx >= 0 && idx < estudios.size()) {
                estudios.remove(idx);
                saveEstudios(personaId, m, estudios, cleanObservacion(obs));
            }
        }
        return Map.of("eliminado", true);
    }

    @PutMapping
    @Transactional
    public Map<String, Object> put(@PathVariable long personaId, @RequestBody Map<String, Object> p) {
        String descPost = t(p.get("estadoEstudio") != null ? p.get("estadoEstudio") : p.get("descripcionPostgrado"));
        String descDoc = t(p.get("campoEducacion") != null ? p.get("campoEducacion") : p.get("descripcionDoctorado"));
        String descMag = t(p.get("anioGraduacion") != null ? p.get("anioGraduacion") : p.get("descripcionMagister"));
        String postVal = (descPost != null && !descPost.isBlank()) || "S".equals(b(p.get("postgrado"))) ? "S" : "N";
        String docVal = (descDoc != null && !descDoc.isBlank()) || "S".equals(b(p.get("doctorado"))) ? "S" : "N";
        String magVal = (descMag != null && !descMag.isBlank()) || "S".equals(b(p.get("magister"))) ? "S" : "N";

        List<Map<String, Object>> estudios = null;
        if (p.get("estudios") instanceof List<?>) {
            estudios = (List<Map<String, Object>>) p.get("estudios");
        } else {
            var r = db.queryForList("select observacion from sira.per_persona_datos_academicos where id_persona=?", personaId);
            if (!r.isEmpty()) estudios = parseEstudios(t(r.get(0).get("observacion")), r.get(0));
        }

        Long t1 = n(p.get("titulo1Id") != null ? p.get("titulo1Id") : p.get("id_titulo_1"));
        Long e1 = n(p.get("especialidadId"));
        String tit2 = t(p.get("institucion") != null ? p.get("institucion") : (p.get("titulo2") != null ? p.get("titulo2") : p.get("titulo_2")));

        if (estudios != null && !estudios.isEmpty()) {
            var prim = estudios.get(0);
            if (t1 == null) t1 = n(prim.get("tituloId"));
            if (e1 == null) e1 = n(prim.get("especialidadId"));
            if (tit2 == null || tit2.isBlank()) tit2 = t(prim.get("institucion"));
            if (descMag == null || descMag.isBlank()) descMag = t(prim.get("anioGraduacion"));
        }

        Long t2 = n(p.get("titulo2Id") != null ? p.get("titulo2Id") : p.get("id_titulo_2"));
        Long e2 = n(p.get("especialidad2Id"));
        if (estudios != null && estudios.size() > 1) {
            var sec = estudios.get(1);
            if (t2 == null) t2 = n(sec.get("tituloId"));
            if (e2 == null) e2 = n(sec.get("especialidadId"));
        }

        String tit1 = t(p.get("titulo"));
        if ((tit1 == null || tit1.isBlank()) && t1 != null) {
            var r = db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?", t1);
            if (!r.isEmpty()) tit1 = String.valueOf(r.get(0).get("descripcion"));
        }

        String obsUser = t(p.get("observacion"));
        String obsFinal = buildObservacionWithEstudios(obsUser, estudios);

        db.update("""
            insert into sira.per_persona_datos_academicos(id_persona,id_nivel,id_titulo_1,id_especialidad,id_titulo_2,id_especialidad_2,postgrado,descripcion_postgrado,doctorado,descripcion_doctorado,magister,descripcion_magister,id_honor,ocupacion_1,ocupacion_2,ocupacion_3,id_disponibilidad,id_modalidad,observacion,titulo,titulo_2,fecha_actualizacion,usuario_actualizacion)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,clock_timestamp(),current_user)
            on conflict(id_persona) do update set id_nivel=excluded.id_nivel,id_titulo_1=excluded.id_titulo_1,id_especialidad=excluded.id_especialidad,id_titulo_2=excluded.id_titulo_2,id_especialidad_2=excluded.id_especialidad_2,postgrado=excluded.postgrado,descripcion_postgrado=excluded.descripcion_postgrado,doctorado=excluded.doctorado,descripcion_doctorado=excluded.descripcion_doctorado,magister=excluded.magister,descripcion_magister=excluded.descripcion_magister,id_honor=excluded.id_honor,ocupacion_1=excluded.ocupacion_1,ocupacion_2=excluded.ocupacion_2,ocupacion_3=excluded.ocupacion_3,id_disponibilidad=excluded.id_disponibilidad,id_modalidad=excluded.id_modalidad,observacion=excluded.observacion,titulo=excluded.titulo,titulo_2=excluded.titulo_2,fecha_actualizacion=clock_timestamp(),usuario_actualizacion=current_user
        """, personaId, n(p.get("nivelId")), t1, e1, t2, e2, postVal, descPost, docVal, descDoc, magVal, descMag, n(p.get("honorId")), t(p.get("ocupacion1")), t(p.get("ocupacion2")), t(p.get("ocupacion3")), n(p.get("disponibilidadId")), n(p.get("modalidadId")), obsFinal, tit1, tit2);

        return Map.of("guardado", true);
    }

    private void saveEstudios(long personaId, Map<String, Object> current, List<Map<String, Object>> estudios, String cleanObs) {
        String obsFinal = buildObservacionWithEstudios(cleanObs, estudios);
        Long t1 = current.get("id_titulo_1") != null ? n(current.get("id_titulo_1")) : null;
        Long e1 = current.get("id_especialidad") != null ? n(current.get("id_especialidad")) : null;
        String inst1 = t(current.get("titulo_2"));
        String anio1 = t(current.get("descripcion_magister"));

        if (!estudios.isEmpty()) {
            var prim = estudios.get(0);
            t1 = n(prim.get("tituloId"));
            e1 = n(prim.get("especialidadId"));
            inst1 = t(prim.get("institucion"));
            anio1 = t(prim.get("anioGraduacion"));
        } else {
            t1 = null; e1 = null; inst1 = ""; anio1 = "";
        }

        Long t2 = estudios.size() > 1 ? n(estudios.get(1).get("tituloId")) : null;
        Long e2 = estudios.size() > 1 ? n(estudios.get(1).get("especialidadId")) : null;

        String tit1 = "";
        if (t1 != null) {
            var r = db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?", t1);
            if (!r.isEmpty()) tit1 = String.valueOf(r.get(0).get("descripcion"));
        }

        db.update("""
            insert into sira.per_persona_datos_academicos(id_persona,id_titulo_1,id_especialidad,id_titulo_2,id_especialidad_2,titulo,titulo_2,descripcion_magister,observacion,fecha_actualizacion,usuario_actualizacion)
            values(?,?,?,?,?,?,?,?,?,clock_timestamp(),current_user)
            on conflict(id_persona) do update set id_titulo_1=excluded.id_titulo_1,id_especialidad=excluded.id_especialidad,id_titulo_2=excluded.id_titulo_2,id_especialidad_2=excluded.id_especialidad_2,titulo=excluded.titulo,titulo_2=excluded.titulo_2,descripcion_magister=excluded.descripcion_magister,observacion=excluded.observacion,fecha_actualizacion=clock_timestamp(),usuario_actualizacion=current_user
        """, personaId, t1, e1, t2, e2, tit1, inst1, anio1, obsFinal);
    }

    private static final String TAG_PREFIX = "<!--ESTUDIOS_JSON:";
    private static final String TAG_SUFFIX = ":ESTUDIOS_JSON-->";

    private List<Map<String, Object>> parseEstudios(String obs, Map<String, Object> row) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (obs != null) {
            String json = null;
            if (obs.contains(TAG_PREFIX) && obs.contains(TAG_SUFFIX)) {
                int start = obs.indexOf(TAG_PREFIX) + TAG_PREFIX.length();
                int end = obs.indexOf(TAG_SUFFIX, start);
                if (end > start) json = obs.substring(start, end).trim();
            } else if (obs.contains("[ESTUDIOS_JSON:") && obs.contains("]")) {
                int start = obs.indexOf("[ESTUDIOS_JSON:") + "[ESTUDIOS_JSON:".length();
                int end = obs.lastIndexOf("]");
                if (end > start) json = obs.substring(start, end).trim();
            }
            if (json != null && !json.isBlank()) {
                try {
                    List<?> parsed = mapper.readValue(json, List.class);
                    for (Object item : parsed) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> casted = new HashMap<>();
                            for (var e : m.entrySet()) casted.put(String.valueOf(e.getKey()), e.getValue());
                            list.add(casted);
                        }
                    }
                    if (!list.isEmpty()) return list;
                } catch (Exception ignored) {}
            }
        }
        Long t1 = row.get("id_titulo_1") != null ? n(row.get("id_titulo_1")) : null;
        Long e1 = row.get("id_especialidad") != null ? n(row.get("id_especialidad")) : null;
        String inst1 = t(row.get("titulo_2"));
        String anio1 = t(row.get("descripcion_magister"));
        if (t1 != null || e1 != null || (inst1 != null && !inst1.isBlank())) {
            Map<String, Object> est = new HashMap<>();
            est.put("tituloId", t1);
            est.put("especialidadId", e1);
            est.put("institucion", inst1 != null ? inst1 : "");
            est.put("anioGraduacion", anio1 != null ? anio1 : "");
            list.add(est);
        }
        return list;
    }

    private void enrichEstudios(List<Map<String, Object>> estudios) {
        for (int i = 0; i < estudios.size(); i++) {
            var est = estudios.get(i);
            est.put("idx", i);
            Long tid = n(est.get("tituloId"));
            if (tid != null && !est.containsKey("titulo")) {
                var q = db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo = ?", tid);
                if (!q.isEmpty()) est.put("titulo", q.get(0).get("descripcion"));
            }
            Long eid = n(est.get("especialidadId"));
            if (eid != null && !est.containsKey("especialidad")) {
                var q = db.queryForList("select descripcion from sira.cat_especialidad where id_especialidad = ?", eid);
                if (!q.isEmpty()) est.put("especialidad", q.get(0).get("descripcion"));
            }
        }
    }

    private String cleanObservacion(String obs) {
        if (obs == null) return "";
        String s = obs;
        if (s.contains(TAG_PREFIX) && s.contains(TAG_SUFFIX)) {
            int start = s.indexOf(TAG_PREFIX);
            int end = s.indexOf(TAG_SUFFIX, start) + TAG_SUFFIX.length();
            s = s.substring(0, start) + s.substring(end);
        }
        if (s.contains("[ESTUDIOS_JSON:") && s.contains("]")) {
            int start = s.indexOf("[ESTUDIOS_JSON:");
            int end = s.lastIndexOf("]") + 1;
            s = s.substring(0, start) + s.substring(end);
        }
        return s.trim();
    }

    private String buildObservacionWithEstudios(String cleanObs, List<Map<String, Object>> estudios) {
        String clean = cleanObs != null ? cleanObservacion(cleanObs) : "";
        if (estudios == null || estudios.isEmpty()) return clean;
        try {
            List<Map<String, Object>> compact = new ArrayList<>();
            for (var est : estudios) {
                Map<String, Object> c = new HashMap<>();
                c.put("tituloId", n(est.get("tituloId")));
                c.put("especialidadId", n(est.get("especialidadId")));
                c.put("institucion", t(est.get("institucion")));
                c.put("anioGraduacion", t(est.get("anioGraduacion")));
                compact.add(c);
            }
            String json = mapper.writeValueAsString(compact);
            return TAG_PREFIX + json + TAG_SUFFIX + (clean.isBlank() ? "" : " " + clean);
        } catch (Exception e) {
            return clean;
        }
    }

    private Long n(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : Long.valueOf(String.valueOf(v));
    }

    private String t(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private String b(Object v) {
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)) ? "S" : "N";
    }
}

