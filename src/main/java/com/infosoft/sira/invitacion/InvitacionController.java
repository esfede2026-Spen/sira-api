package com.infosoft.sira.invitacion;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/v1/invitaciones")
public class InvitacionController {
 private final JdbcTemplate db;
 public InvitacionController(JdbcTemplate db){this.db=db;}

 @GetMapping public List<Map<String,Object>> listar(@RequestParam(required=false) Long referidoId){
  String base="""
   select i.id_invitacion,i.uuid_invitacion,i.id_referido,i.nombre_declarado,i.apellido_declarado,
          c.codigo canal,i.fecha_creacion,i.fecha_vencimiento,i.fecha_uso,i.estado,i.activo,
          p.primer_nombre referido_nombre,p.primer_apellido referido_apellido
   from sira.inv_invitacion i join sira.per_persona p on p.id_persona=i.id_referido
   left join sira.cat_canal c on c.id_canal=i.id_canal
   """;
  return referidoId==null?db.queryForList(base+" order by i.id_invitacion desc"):db.queryForList(base+" where i.id_referido=? order by i.id_invitacion desc",referidoId);
 }

 @PostMapping @Transactional
 public Map<String,Object> crear(@RequestBody Map<String,Object> p){
  long referidoId=Long.parseLong(String.valueOf(p.get("referidoId")));
  Integer existe=db.queryForObject("select count(*) from sira.per_persona where id_persona=? and estado_registro='ACTIVO'",Integer.class,referidoId);
  if(existe==null||existe==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La persona referidora no existe o está inactiva");
  String token=UUID.randomUUID()+"."+UUID.randomUUID();
  String hash=sha256(token);
  int horas=72;
  try{horas=Integer.parseInt(String.valueOf(p.getOrDefault("vigenciaHoras","72")));}catch(Exception ignored){}
  int canalId=canal(String.valueOf(p.getOrDefault("canal","ENLACE")));
  Long id=db.queryForObject("""
   insert into sira.inv_invitacion(uuid_invitacion,id_referido,nombre_declarado,apellido_declarado,id_canal,token_hash,fecha_vencimiento,estado,activo)
   values(gen_random_uuid(),?,?,?,?,?,clock_timestamp()+make_interval(hours => ?),'PENDIENTE','S') returning id_invitacion
   """,Long.class,referidoId,text(p.get("nombre")),text(p.get("apellido")),canalId,hash,horas);
  return Map.of("idInvitacion",Objects.requireNonNull(id),"token",token,"vigenciaHoras",horas);
 }

 @PostMapping("/{id}/revocar") public Map<String,Object> revocar(@PathVariable long id){db.update("update sira.inv_invitacion set estado='REVOCADO',activo='N' where id_invitacion=?",id);return Map.of("revocada",true);}

 @PostMapping("/publico/validar") @Transactional
 public Map<String,Object> validar(@RequestBody Map<String,Object> p){
  String token=String.valueOf(p.get("token"));String hash=sha256(token);
  var rows=db.queryForList("""
   select i.id_invitacion,i.id_referido,i.nombre_declarado,i.apellido_declarado,i.estado,i.fecha_vencimiento,
          p.primer_nombre referido_nombre,p.primer_apellido referido_apellido
   from sira.inv_invitacion i join sira.per_persona p on p.id_persona=i.id_referido
   where i.token_hash=? and i.activo='S' limit 1
   """,hash);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitación inválida");
  Map<String,Object> r=rows.get(0);
  Boolean vencida=db.queryForObject("select fecha_vencimiento <= clock_timestamp() from sira.inv_invitacion where id_invitacion=?",Boolean.class,r.get("id_invitacion"));
  if(Boolean.TRUE.equals(vencida)){
   db.update("update sira.inv_invitacion set estado='VENCIDO',activo='N' where id_invitacion=?",r.get("id_invitacion"));
   throw new ResponseStatusException(HttpStatus.GONE,"Invitación vencida");
  }
  if(!List.of("PENDIENTE","ABIERTO").contains(String.valueOf(r.get("estado"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invitación no disponible");
  db.update("update sira.inv_invitacion set estado='ABIERTO',fecha_primer_acceso=coalesce(fecha_primer_acceso,clock_timestamp()),fecha_ultimo_acceso=clock_timestamp() where id_invitacion=?",r.get("id_invitacion"));
  return r;
 }

 private int canal(String codigo){List<Map<String,Object>> r=db.queryForList("select id_canal from sira.cat_canal where codigo=? and activo='S'",codigo);return r.isEmpty()?db.queryForObject("select id_canal from sira.cat_canal where codigo='ENLACE'",Integer.class):((Number)r.get(0).get("id_canal")).intValue();}
 private String sha256(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}catch(Exception e){throw new IllegalStateException(e);}}
 private String text(Object v){return v==null?null:String.valueOf(v).trim();}
}
