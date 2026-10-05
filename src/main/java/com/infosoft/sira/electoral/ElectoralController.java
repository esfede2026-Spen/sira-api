package com.infosoft.sira.electoral;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/electoral")
public class ElectoralController {
 private final JdbcTemplate db;public ElectoralController(JdbcTemplate db){this.db=db;}
 @GetMapping("/consultas/persona/{personaId}") public List<Map<String,Object>> consultas(@PathVariable long personaId){return db.queryForList("select id_consulta_electoral,id_persona,documento_numero,proveedor,fecha_consulta,resultado,codigo_respuesta,mensaje_respuesta from sira.ele_consulta where id_persona=? order by fecha_consulta desc",personaId);}
 @PostMapping("/consultas/mock/{personaId}") public Map<String,Object> mock(@PathVariable long personaId){Map<String,Object>p=db.queryForMap("select id_persona,documento_numero,primer_nombre,primer_apellido from sira.per_persona where id_persona=?",personaId);return Map.of("proveedor","MOCK_CEDULA_COM_VE","estadoProcesamiento","COMPLETADA","resultadoPersona","ENCONTRADA","resultadoElectoral","ANTIGUO","mensajePantalla","La consulta de datos aqui presentada pueden estar o no actualizados con el CNE.","persona",p,"datosElectorales",Map.of("estado","MIRANDA","municipio","CHACAO","parroquia","CHACAO","centroElectoral","COLEGIO SCHONTHAL"));}
}
