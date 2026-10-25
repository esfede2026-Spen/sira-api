package com.infosoft.sira.contacto;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class CodigoTelefonicoService {
    private final JdbcTemplate db;

    public CodigoTelefonicoService(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> listar() {
        return db.queryForList("""
            select id_codigo_telefonico,
                   codigo_pais,
                   codigo_area_operador,
                   regexp_replace(codigo_area_operador, '^0+', '') as codigo_area_sin_cero,
                   descripcion,
                   tipo_linea
              from sira.cat_codigo_telefonico
             where activo = 'S'
             order by case when codigo_pais = '+58' then 0 else 1 end,
                      tipo_linea,
                      codigo_area_operador
            """);
    }

    @Transactional
    public Long resolverOCrear(String pais, String area) {
        String p = (pais == null || pais.isBlank()) ? "+58" : pais.trim();
        if (!p.startsWith("+")) p = "+" + p;
        String a = area == null ? "" : area.replaceAll("[^0-9]", "");
        if (a.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de área es obligatorio");
        }
        String aConCero = a.startsWith("0") ? a : ("0" + a);
        String aSinCero = a.replaceAll("^0+", "");
        if (aSinCero.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de área debe tener exactamente 3 dígitos");
        }

        List<Map<String, Object>> rows = db.queryForList("""
            select id_codigo_telefonico
              from sira.cat_codigo_telefonico
             where activo = 'S'
               and codigo_pais = ?
               and (codigo_area_operador = ? or codigo_area_operador = ? or regexp_replace(codigo_area_operador, '^0+', '') = ?)
             limit 1
            """, p, a, aConCero, aSinCero);

        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("id_codigo_telefonico")).longValue();
        }

        boolean esVenezuela = "+58".equals(p);
        boolean esMovil = (esVenezuela && List.of("414", "424", "412", "422", "416", "426").contains(aSinCero)) || !esVenezuela;
        String tipo = esMovil ? "CELULAR" : "FIJO";
        String valorArea = esVenezuela ? aConCero : a;
        String desc = esVenezuela ? ("Código " + aSinCero) : ("Internacional " + p + " " + a);

        Long idPais = 1L;
        if (!esVenezuela) {
            List<Long> pList = db.queryForList("select id_pais from sira.pais where activo='S' order by case when codigo_iso2='VE' then 1 else 0 end, id_pais limit 1", Long.class);
            if (!pList.isEmpty()) {
                idPais = pList.get(0);
            }
        }

        return db.queryForObject("""
            insert into sira.cat_codigo_telefonico(id_pais, codigo_pais, codigo_area_operador, descripcion, tipo_linea, activo)
            values (?, ?, ?, ?, ?, 'S')
            returning id_codigo_telefonico
            """, Long.class, idPais, p, valorArea, desc, tipo);
    }
}
