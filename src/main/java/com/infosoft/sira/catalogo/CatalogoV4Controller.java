package com.infosoft.sira.catalogo;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/catalogos-v4")
public class CatalogoV4Controller{private final JdbcTemplate db;public CatalogoV4Controller(JdbcTemplate db){this.db=db;}
@GetMapping("/nacionalidades") public List<Map<String,Object>> nacionalidades(){return db.queryForList("select id_nacionalidad_catalogo,codigo,descripcion,id_pais from sira.cat_nacionalidad where activo='S' order by case when codigo='VE' then 0 else 1 end,descripcion");}
@GetMapping("/niveles") public List<Map<String,Object>> niveles(){return q("cat_nivel_educativo","id_nivel");}
@GetMapping("/especialidades") public List<Map<String,Object>> especialidades(){return q("cat_especialidad","id_especialidad");}
@GetMapping("/honores") public List<Map<String,Object>> honores(){return q("cat_honor_academico","id_honor");}
@GetMapping("/modalidades") public List<Map<String,Object>> modalidades(){return q("cat_modalidad","id_modalidad");}
@GetMapping("/disponibilidades") public List<Map<String,Object>> disponibilidades(){return q("cat_disponibilidad","id_disponibilidad");}
private List<Map<String,Object>> q(String tabla,String id){return db.queryForList("select "+id+",descripcion from sira."+tabla+" where activo='S' order by descripcion");}}
