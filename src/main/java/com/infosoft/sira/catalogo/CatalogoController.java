package com.infosoft.sira.catalogo;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/catalogos")
public class CatalogoController {
 private final JdbcTemplate db;public CatalogoController(JdbcTemplate db){this.db=db;}
 @GetMapping("/paises") public List<Map<String,Object>> paises(){return db.queryForList("select id_pais,codigo_iso2,codigo_iso3,desc_pais,activo from sira.pais where activo='S' order by desc_pais");}
 @GetMapping("/tipos-documento") public List<Map<String,Object>> tiposDocumento(){return db.queryForList("select id_tipo_documento,codigo,descripcion,solo_numerico,longitud_minima,longitud_maxima from sira.cat_tipo_documento where activo='S' order by id_tipo_documento");}
 @GetMapping("/sexos") public List<Map<String,Object>> sexos(){return db.queryForList("select id_sexo,codigo,descripcion from sira.cat_sexo where activo='S' order by descripcion");}
 @GetMapping("/tipos-contacto") public List<Map<String,Object>> tiposContacto(){return db.queryForList("select id_tipo_contacto,codigo,descripcion,permite_otp from sira.cat_tipo_contacto where activo='S' order by id_tipo_contacto");}
 @GetMapping("/codigos-telefonicos") public List<Map<String,Object>> codigos(){return db.queryForList("select id_codigo_telefonico,id_pais,codigo_pais,codigo_area_operador,descripcion,tipo_linea from sira.cat_codigo_telefonico where activo='S' order by codigo_pais,codigo_area_operador");}
}
