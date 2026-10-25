package com.infosoft.sira.residencia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/personas/{personaId}/residencia")
public class ResidenciaController {
 private final JdbcTemplate db;
 public ResidenciaController(JdbcTemplate db){this.db=db;}
 @GetMapping("/principal")
 public Map<String,Object> principal(@PathVariable long personaId){
  var rows=db.queryForList("""
   select r.*,s.id_tipo_sector_geografico,p.desc_pais,e.desc_estado,c.desc_ciudad,m.desc_municipio,q.desc_parroquia,
          s.desc_sector_geografico,cp.codigo_postal
   from sira.per_persona_residencia r
   join sira.pais p on p.id_pais=r.id_pais join sira.estado e on e.id_estado=r.id_estado
   left join sira.ciudad c on c.id_ciudad=r.id_ciudad join sira.municipio m on m.id_municipio=r.id_municipio
   join sira.parroquia q on q.id_parroquia=r.id_parroquia left join sira.sector_geografico s on s.id_sector_geografico=r.id_sector_geografico
   left join sira.codigo_postal cp on cp.id_codigo_postal=r.id_codigo_postal
   where r.id_persona=? and r.principal='S' and r.activo='S' limit 1
   """,personaId);
  String ubi = "";
  Long tipoSecBackup = null;
  try {
      var list = db.queryForList("select motivo_no_captura, precision_metros from sira.per_persona_geolocalizacion where id_persona=? and eliminada_usuario='N' order by id_geolocalizacion desc limit 1", personaId);
      if(!list.isEmpty()){
          if(list.get(0).get("motivo_no_captura") != null) ubi = String.valueOf(list.get(0).get("motivo_no_captura"));
          if(list.get(0).get("precision_metros") != null) tipoSecBackup = ((Number)list.get(0).get("precision_metros")).longValue();
      }
  } catch(Exception ignored){}
  if(rows.isEmpty()){
      return Map.of("existe",false,"ubicacion",ubi);
  }
  Map<String,Object> res = new LinkedHashMap<>(rows.get(0));
  res.put("ubicacion", ubi);
  if(res.get("id_tipo_sector_geografico") == null && tipoSecBackup != null){
      res.put("id_tipo_sector_geografico", tipoSecBackup);
  }
  return Map.of("existe",true,"residencia",res,"ubicacion",ubi);
 }
 @PutMapping("/principal") @Transactional
 public Map<String,Object> guardar(@PathVariable long personaId,@RequestBody Map<String,Object> p){
  db.update("update sira.per_persona_residencia set activo='N',principal='N',fecha_hasta=current_date where id_persona=? and activo='S'",personaId);
  Long id=db.queryForObject("""
   insert into sira.per_persona_residencia(id_persona,id_pais,id_estado,id_ciudad,id_municipio,id_parroquia,id_sector_geografico,id_codigo_postal,direccion_complementaria,principal,activo)
   values(?,?,?,?,?,?,?,?,?,'S','S') returning id_persona_residencia
   """,Long.class,personaId,num(p,"paisId"),num(p,"estadoId"),nullable(p.get("ciudadId")),num(p,"municipioId"),num(p,"parroquiaId"),nullable(p.get("sectorId")),nullable(p.get("codigoPostalId")),text(p.get("direccionComplementaria")));
  String ubi = text(p.get("ubicacion"));
  Long tipoSec = nullable(p.get("tipoSectorId"));
  if((ubi != null && !ubi.isBlank()) || tipoSec != null){
      try {
          var existingGeo = db.queryForList("select latitud, longitud, precision_metros, metodo, resultado_captura, motivo_no_captura from sira.per_persona_geolocalizacion where id_persona=? and eliminada_usuario='N' order by id_geolocalizacion desc limit 1", personaId);
          Double lat = null, lon = null;
          String metodo = "MANUAL", resultado = "NO_DISPONIBLE";
          Double prec = tipoSec != null ? tipoSec.doubleValue() : null;
          String mot = ubi != null ? ubi.trim() : "";
          if(!existingGeo.isEmpty()){
              var eg = existingGeo.get(0);
              if(eg.get("latitud") != null && eg.get("longitud") != null){
                  lat = ((Number)eg.get("latitud")).doubleValue();
                  lon = ((Number)eg.get("longitud")).doubleValue();
                  metodo = "GPS";
                  resultado = "CAPTURADA";
                  if(eg.get("precision_metros") != null) prec = ((Number)eg.get("precision_metros")).doubleValue();
              }
              String prevMot = text(eg.get("motivo_no_captura"));
              if(prevMot != null && prevMot.contains("[PAIS:") && prevMot.contains("]")){
                  String tag = prevMot.substring(prevMot.indexOf("[PAIS:"), prevMot.indexOf("]") + 1);
                  if(!mot.contains("[PAIS:")) mot = (mot.isBlank() ? tag : (mot + " " + tag));
              }
          }
          db.update("update sira.per_persona_geolocalizacion set eliminada_usuario='S' where id_persona=? and eliminada_usuario='N'", personaId);
          if(lat != null && lon != null){
              db.update("insert into sira.per_persona_geolocalizacion(id_persona, id_persona_residencia, latitud, longitud, precision_metros, metodo, resultado_captura, motivo_no_captura, consentimiento, eliminada_usuario) values(?, ?, ?, ?, ?, ?, ?, ?, 'S', 'N')", personaId, id, lat, lon, prec, metodo, resultado, mot);
          } else {
              db.update("insert into sira.per_persona_geolocalizacion(id_persona, id_persona_residencia, precision_metros, metodo, resultado_captura, motivo_no_captura, consentimiento, eliminada_usuario) values(?, ?, ?, 'MANUAL', 'NO_DISPONIBLE', ?, 'S', 'N')", personaId, id, prec, mot);
          }
      } catch(Exception ignored){}
  }
  if(nullable(p.get("sectorId"))!=null) db.update("update sira.per_persona set registro_fase1_completo='S' where id_persona=?",personaId);
  return Map.of("idPersonaResidencia",Objects.requireNonNull(id),"fase1Completa",nullable(p.get("sectorId"))!=null);
 }
 private long num(Map<String,Object> p,String k){return Long.parseLong(String.valueOf(p.get(k)));}
 private Long nullable(Object v){if(v==null||String.valueOf(v).isBlank())return null;return Long.parseLong(String.valueOf(v));}
 private String text(Object v){return v==null?null:String.valueOf(v).trim();}
}
