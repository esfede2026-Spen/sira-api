package com.infosoft.sira.catalogo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/catalogos-v4")
public class CatalogoV4Controller {
    private final JdbcTemplate db;
    public static final Map<Long, List<Long>> ESP_POR_TITULO = Map.of(
        1L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 43L, 44L, 45L, 46L, 47L, 48L, 55L, 56L, 57L, 58L, 59L, 62L, 63L, 64L, 65L, 66L, 67L),
        2L, List.of(21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L, 41L, 42L, 48L, 62L, 63L, 64L),
        3L, List.of(49L, 50L, 51L, 52L, 53L, 54L),
        4L, List.of(60L, 61L)
    );

    public CatalogoV4Controller(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/nacionalidades")
    public List<Map<String,Object>> nacionalidades() {
        return db.queryForList("select id_nacionalidad_catalogo,codigo,descripcion,id_pais from sira.cat_nacionalidad where activo='S' order by case when codigo='VE' then 0 else 1 end,descripcion");
    }

    @GetMapping("/titulos")
    public List<Map<String,Object>> titulos() {
        return db.queryForList("select id_titulo,descripcion from sira.cat_titulo_academico where activo='S' order by id_titulo");
    }

    @GetMapping("/niveles")
    public List<Map<String,Object>> niveles() {
        return db.queryForList("""
            select id_nivel,descripcion from sira.cat_nivel_educativo where activo='S' 
            order by case 
              when descripcion like '%ISCED 0%' then 0
              when descripcion like '%ISCED 1%' then 1
              when descripcion like '%ISCED 2%' then 2
              when descripcion like '%ISCED 3%' then 3
              when descripcion like '%ISCED 4%' then 4
              when descripcion like '%ISCED 5%' then 5
              when descripcion like '%ISCED 6%' then 6
              when descripcion like '%ISCED 7%' then 7
              when descripcion like '%ISCED 8%' then 8
              when descripcion like '%ISCED 9%' then 9
              else id_nivel end
        """);
    }

    @GetMapping("/especialidades")
    public List<Map<String,Object>> especialidades(@RequestParam(required = false) Long tituloId) {
        var list = db.queryForList("select id_especialidad,descripcion from sira.cat_especialidad where activo='S' order by descripcion");
        List<Map<String,Object>> res = new ArrayList<>();
        for (var row : list) {
            Long eid = ((Number) row.get("id_especialidad")).longValue();
            List<Long> titulosCompatibles = new ArrayList<>();
            for (var entry : ESP_POR_TITULO.entrySet()) {
                if (entry.getValue().contains(eid)) {
                    titulosCompatibles.add(entry.getKey());
                }
            }
            if (tituloId == null || titulosCompatibles.contains(tituloId)) {
                Map<String,Object> m = new HashMap<>(row);
                m.put("titulosCompatibles", titulosCompatibles);
                res.add(m);
            }
        }
        return res;
    }

    @GetMapping("/honores")
    public List<Map<String,Object>> honores() {
        return q("cat_honor_academico","id_honor");
    }

    @GetMapping("/modalidades")
    public List<Map<String,Object>> modalidades() {
        return db.queryForList("""
            select id_modalidad,descripcion from sira.cat_modalidad where activo='S'
            order by case 
              when descripcion = 'Presencial' then 1
              when descripcion = 'Remota' then 2
              when descripcion = 'Híbrida' then 3
              when descripcion = 'Otra' then 4
              else 99 end
        """);
    }

    @GetMapping("/disponibilidades")
    public List<Map<String,Object>> disponibilidades() {
        return db.queryForList("""
            select id_disponibilidad,descripcion from sira.cat_disponibilidad where activo='S'
            order by case 
              when descripcion = 'Por proyecto' then 1
              when descripcion = 'Tiempo parcial' then 2
              when descripcion = 'Tiempo completo' then 3
              when descripcion = 'No disponible' then 4
              when descripcion = 'Otra' then 5
              else 99 end
        """);
    }

    private List<Map<String,Object>> q(String t,String id) {
        return db.queryForList("select "+id+",descripcion from sira."+t+" where activo='S' order by descripcion");
    }
}

