package com.infosoft.sira.academico;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/personas/{personaId}/academico") public class AcademicoController{private final JdbcTemplate db;public AcademicoController(JdbcTemplate db){this.db=db;}
@GetMapping public Map<String,Object> get(@PathVariable long personaId){var r=db.queryForList("select * from sira.per_persona_datos_academicos where id_persona=?",personaId);return r.isEmpty()?Map.of():r.get(0);}
@PutMapping @Transactional public Map<String,Object> put(@PathVariable long personaId,@RequestBody Map<String,Object> p){
    String descPost=t(p.get("descripcionPostgrado"));
    String descDoc=t(p.get("descripcionDoctorado"));
    String descMag=t(p.get("descripcionMagister"));
    String postVal=(descPost!=null&&!descPost.isBlank())||"S".equals(b(p.get("postgrado")))?"S":"N";
    String docVal=(descDoc!=null&&!descDoc.isBlank())||"S".equals(b(p.get("doctorado")))?"S":"N";
    String magVal=(descMag!=null&&!descMag.isBlank())||"S".equals(b(p.get("magister")))?"S":"N";
    Long t1=n(p.get("titulo1Id")!=null?p.get("titulo1Id"):p.get("id_titulo_1"));
    Long t2=n(p.get("titulo2Id")!=null?p.get("titulo2Id"):p.get("id_titulo_2"));
    String tit1=t(p.get("titulo"));
    String tit2=t(p.get("titulo2")!=null?p.get("titulo2"):p.get("titulo_2"));
    if((tit1==null||tit1.isBlank())&&t1!=null){
        var r=db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?",t1);
        if(!r.isEmpty())tit1=String.valueOf(r.get(0).get("descripcion"));
    }
    if((tit2==null||tit2.isBlank())&&t2!=null){
        var r=db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?",t2);
        if(!r.isEmpty())tit2=String.valueOf(r.get(0).get("descripcion"));
    }
    db.update("""
insert into sira.per_persona_datos_academicos(id_persona,id_nivel,id_titulo_1,id_especialidad,id_titulo_2,id_especialidad_2,postgrado,descripcion_postgrado,doctorado,descripcion_doctorado,magister,descripcion_magister,id_honor,ocupacion_1,ocupacion_2,ocupacion_3,id_disponibilidad,id_modalidad,observacion,titulo,titulo_2)
values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id_persona) do update set id_nivel=excluded.id_nivel,id_titulo_1=excluded.id_titulo_1,id_especialidad=excluded.id_especialidad,id_titulo_2=excluded.id_titulo_2,id_especialidad_2=excluded.id_especialidad_2,postgrado=excluded.postgrado,descripcion_postgrado=excluded.descripcion_postgrado,doctorado=excluded.doctorado,descripcion_doctorado=excluded.descripcion_doctorado,magister=excluded.magister,descripcion_magister=excluded.descripcion_magister,id_honor=excluded.id_honor,ocupacion_1=excluded.ocupacion_1,ocupacion_2=excluded.ocupacion_2,ocupacion_3=excluded.ocupacion_3,id_disponibilidad=excluded.id_disponibilidad,id_modalidad=excluded.id_modalidad,observacion=excluded.observacion,titulo=excluded.titulo,titulo_2=excluded.titulo_2,fecha_actualizacion=clock_timestamp(),usuario_actualizacion=current_user
""",personaId,n(p.get("nivelId")),t1,n(p.get("especialidadId")),t2,n(p.get("especialidad2Id")),postVal,descPost,docVal,descDoc,magVal,descMag,n(p.get("honorId")),t(p.get("ocupacion1")),t(p.get("ocupacion2")),t(p.get("ocupacion3")),n(p.get("disponibilidadId")),n(p.get("modalidadId")),t(p.get("observacion")),tit1,tit2);return Map.of("guardado",true);}
private Long n(Object v){return v==null||String.valueOf(v).isBlank()?null:Long.valueOf(String.valueOf(v));}private String t(Object v){return v==null?null:String.valueOf(v).trim();}private String b(Object v){return Boolean.TRUE.equals(v)||"true".equalsIgnoreCase(String.valueOf(v))?"S":"N";}}
