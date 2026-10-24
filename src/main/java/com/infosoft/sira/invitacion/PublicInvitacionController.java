package com.infosoft.sira.invitacion;

import com.infosoft.sira.contacto.CodigoTelefonicoService;
import com.infosoft.sira.electoral.ElectoralController;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/publico/invitaciones")
public class PublicInvitacionController {
 private final InvitacionController service; private final JdbcTemplate db; private final ElectoralController electoral; private final CodigoTelefonicoService codigoService;
 public PublicInvitacionController(InvitacionController service,JdbcTemplate db,ElectoralController electoral,CodigoTelefonicoService codigoService){this.service=service;this.db=db;this.electoral=electoral;this.codigoService=codigoService;}
 @PostMapping("/validar") public Map<String,Object> validar(@RequestBody Map<String,Object> p){return service.validar(p);}

 @GetMapping("/codigos-telefonicos") public List<Map<String,Object>> codigos(){return codigoService.listar();}
 @PostMapping("/codigos-telefonicos/resolver") public Map<String,Object> resolver(@RequestBody Map<String,Object> p){
  String cp = text(p.get("codigoPais")), ca = text(p.get("codigoArea"));
  Long id = codigoService.resolverOCrear(cp.isBlank()?"+58":cp, ca);
  return Map.of("idCodigoTelefonico", id, "codigoPais", cp.isBlank()?"+58":cp, "codigoArea", ca);
 }
 @GetMapping("/tipos-contacto") public List<Map<String,Object>> tipos(){return db.queryForList("select id_tipo_contacto,codigo,descripcion,permite_otp from sira.cat_tipo_contacto where activo='S' order by id_tipo_contacto");}
 @GetMapping("/catalogos/titulos") public List<Map<String,Object>> titulos(){return db.queryForList("select id_titulo,descripcion from sira.cat_titulo_academico where activo='S' order by descripcion");}
 @GetMapping("/catalogos/nacionalidades") public List<Map<String,Object>> nacionalidades(){return db.queryForList("select id_nacionalidad_catalogo,descripcion from sira.cat_nacionalidad where activo='S' order by descripcion");}
 @GetMapping("/catalogos/sexos") public List<Map<String,Object>> sexos(){return db.queryForList("select id_sexo,descripcion from sira.cat_sexo where activo='S' order by descripcion");}
 @GetMapping("/catalogos/honores") public List<Map<String,Object>> honores(){return db.queryForList("select id_honor,descripcion from sira.cat_honor_academico where activo='S' order by descripcion");}
 @GetMapping("/catalogos/disponibilidades") public List<Map<String,Object>> disponibilidades(){return db.queryForList("select id_disponibilidad,descripcion from sira.cat_disponibilidad where activo='S' order by descripcion");}
 @GetMapping("/catalogos/niveles") public List<Map<String,Object>> niveles(){return db.queryForList("select id_nivel,descripcion from sira.cat_nivel_educativo where activo='S' order by descripcion");}
 @GetMapping("/catalogos/especialidades") public List<Map<String,Object>> especialidades(){return db.queryForList("select id_especialidad,descripcion from sira.cat_especialidad where activo='S' order by descripcion");}
 @GetMapping("/catalogos/modalidades") public List<Map<String,Object>> modalidades(){return db.queryForList("select id_modalidad,descripcion from sira.cat_modalidad where activo='S' order by descripcion");}
 @GetMapping("/geografia/paises") public List<Map<String,Object>> paises(){return db.queryForList("select id_pais,desc_pais from sira.pais where activo='S' order by desc_pais");}
 @GetMapping("/geografia/estados") public List<Map<String,Object>> estados(@RequestParam long paisId){return db.queryForList("select id_estado,desc_estado from sira.estado where activo='S' and id_pais=? order by desc_estado",paisId);}
 @GetMapping("/geografia/ciudades") public List<Map<String,Object>> ciudades(@RequestParam long estadoId){return db.queryForList("select id_ciudad,desc_ciudad from sira.ciudad where activo='S' and id_estado=? order by desc_ciudad",estadoId);}
 @GetMapping("/geografia/municipios") public List<Map<String,Object>> municipios(@RequestParam long ciudadId){return db.queryForList("select id_municipio,desc_municipio from sira.municipio where activo='S' and id_ciudad=? order by desc_municipio",ciudadId);}
 @GetMapping("/geografia/parroquias") public List<Map<String,Object>> parroquias(@RequestParam long municipioId){return db.queryForList("select id_parroquia,desc_parroquia from sira.parroquia where activo='S' and id_municipio=? order by desc_parroquia",municipioId);}
 @GetMapping("/geografia/tipos-sector") public List<Map<String,Object>> tiposSector(){return db.queryForList("select id_tipo_sector_geografico,desc_tipo_sector_geografico from sira.tipo_sector_geografico where activo='S' order by desc_tipo_sector_geografico");}
 @GetMapping("/geografia/sectores") public List<Map<String,Object>> sectores(@RequestParam(required=false) Long estadoId,@RequestParam(required=false) Long ciudadId,@RequestParam(required=false) Long municipioId,@RequestParam(required=false) Long parroquiaId,@RequestParam long tipoSectorId){return db.queryForList("select id_sector_geografico,desc_sector_geografico from sira.sector_geografico where activo='S' and id_tipo_sector_geografico=? and (? is null or id_estado=?) and (? is null or id_ciudad=?) and (? is null or id_municipio=?) and (? is null or id_parroquia=?) order by desc_sector_geografico",tipoSectorId,estadoId,estadoId,ciudadId,ciudadId,municipioId,municipioId,parroquiaId,parroquiaId);}
 @GetMapping("/geografia/codigos-postales") public List<Map<String,Object>> postales(@RequestParam long sectorId){return db.queryForList("select id_codigo_postal,codigo_postal from sira.codigo_postal where activo='S' and id_sector_geografico=? order by codigo_postal",sectorId);}

 @PostMapping("/preparar")
 public Map<String,Object> preparar(@RequestBody Map<String,Object> p){
  String token=text(p.get("token"));
  Map<String,Object> inv=service.validar(Map.of("token",token));
  long iid=num(inv.get("id_invitacion"));
  String doc=digits(p.get("documentoNumero"));
  if(doc.length()<5) bad("Documento inválido");
  long pais=num(p.getOrDefault("paisEmisorId",1));
  long tipo=num(p.getOrDefault("tipoDocumentoId",1));
  long nac=num(p.getOrDefault("nacionalidadCatalogoId",1));

  var found=db.queryForList("select id_persona, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido from sira.per_persona where id_pais_emisor=? and id_tipo_documento=? and documento_numero=? limit 1",pais,tipo,doc);
  boolean existente=!found.isEmpty();
  Map<String,Object> cne = new LinkedHashMap<>();
  String primerNombre = "";
  String segundoNombre = "";
  String primerApellido = "";
  String segundoApellido = "";
  boolean cneEncontrado = false;

  if(existente){
   primerNombre = text(found.get(0).get("primer_nombre"));
   segundoNombre = text(found.get(0).get("segundo_nombre"));
   primerApellido = text(found.get(0).get("primer_apellido"));
   segundoApellido = text(found.get(0).get("segundo_apellido"));
   Map<String,Object> act = electoral.actual(((Number)found.get(0).get("id_persona")).longValue());
   if(act != null && Boolean.TRUE.equals(act.get("existe")) && act.get("registro") instanceof Map){
    Map<String,Object> reg = (Map<String,Object>) act.get("registro");
    cne.putAll(act);
    cne.put("centroElectoral", reg.get("centro_electoral"));
    cne.put("centro_electoral", reg.get("centro_electoral"));
    cne.put("estado", reg.get("estado"));
    cne.put("municipio", reg.get("municipio"));
    cne.put("parroquia", reg.get("parroquia"));
    cne.put("indicadorRegistroExistente", reg.get("indicador_registro_existente"));
    cneEncontrado = "S".equals(reg.get("indicador_registro_existente")) && reg.get("centro_electoral") != null;
    cne.put("encontrado", cneEncontrado);
    if(reg.get("nombres_fuente") != null){
     String[] nomSep = ElectoralController.separarCompuesto(String.valueOf(reg.get("nombres_fuente")));
     if(primerNombre.isBlank()) primerNombre = nomSep[0];
     if(segundoNombre.isBlank()) segundoNombre = nomSep[1];
    }
    if(reg.get("apellidos_fuente") != null){
     String[] apeSep = ElectoralController.separarCompuesto(String.valueOf(reg.get("apellidos_fuente")));
     if(primerApellido.isBlank()) primerApellido = apeSep[0];
     if(segundoApellido.isBlank()) segundoApellido = apeSep[1];
    }
   }
   if(!cneEncontrado || primerNombre.isBlank() || primerApellido.isBlank()){
    try {
     Map<String,Object> cneDir = electoral.consultarDirecto("V", doc);
     if(cneDir != null && Boolean.TRUE.equals(cneDir.get("encontrado"))){
      cne.putAll(cneDir);
      cne.put("centro_electoral", cneDir.get("centroElectoral"));
      cneEncontrado = true;
      if(primerNombre.isBlank()) primerNombre = text(cneDir.get("primerNombre"));
      if(segundoNombre.isBlank()) segundoNombre = text(cneDir.get("segundoNombre"));
      if(primerApellido.isBlank()) primerApellido = text(cneDir.get("primerApellido"));
      if(segundoApellido.isBlank()) segundoApellido = text(cneDir.get("segundoApellido"));
     }
    } catch(Exception ignored){}
   }
  } else {
   try {
    Map<String,Object> cneDir = electoral.consultarDirecto("V", doc);
    if(cneDir != null && Boolean.TRUE.equals(cneDir.get("encontrado"))){
     cne.putAll(cneDir);
     cne.put("centro_electoral", cneDir.get("centroElectoral"));
     cneEncontrado = true;
     primerNombre = text(cneDir.get("primerNombre"));
     segundoNombre = text(cneDir.get("segundoNombre"));
     primerApellido = text(cneDir.get("primerApellido"));
     segundoApellido = text(cneDir.get("segundoApellido"));
    } else if(cneDir != null) {
     cne.putAll(cneDir);
    }
   } catch(Exception e){
    cne = Map.of("errorProveedor", String.valueOf(e.getMessage()), "indicadorRegistroExistente", "N", "encontrado", false);
   }
  }

  String cp=text(p.get("codigoPais")), ca=text(p.get("codigoArea")), n=digits(p.get("numero")), m=text(p.get("correo"));
  String tel = (cp.isBlank()?"+58":cp) + " " + (ca.isBlank()?"":ca) + (n.isBlank()?"":" "+n);
  Map<String,Object> out=new LinkedHashMap<>();
  out.put("preparado", true);
  out.put("personaExistente", existente);
  out.put("cneEncontrado", cneEncontrado);
  out.put("documentoNumero", doc);
  out.put("primerNombre", primerNombre);
  out.put("segundoNombre", segundoNombre);
  out.put("primerApellido", primerApellido);
  out.put("segundoApellido", segundoApellido);
  out.put("telefono", tel.trim());
  out.put("codigoPais", cp.isBlank()?"+58":cp);
  out.put("codigoArea", ca);
  out.put("numero", n);
  out.put("correo", m);
  out.put("ubicacionReferencia", text(p.get("ubicacionReferencia") != null ? p.get("ubicacionReferencia") : p.get("ubicacion")));
  out.put("electoral", cne);
  return out;
 }

 @PostMapping("/confirmar") @Transactional
 public Map<String,Object> confirmar(@RequestBody Map<String,Object> p){
  try {
   String token=text(p.get("token"));
   Map<String,Object> inv=service.validar(Map.of("token",token));
   long iid=num(inv.get("id_invitacion"));
   Map<String,Object> row=db.queryForMap("select *,to_jsonb(i) datos from sira.inv_invitacion i where id_invitacion=?",iid);
   boolean abierta="S".equals(row.get("es_abierta"));
   String doc=digits(p.get("documentoNumero"));
   if(doc.length()<5) bad("Documento inválido");
   long pais=num(p.getOrDefault("paisEmisorId",1));
   long tipo=num(p.getOrDefault("tipoDocumentoId",1));
   long nac=num(p.getOrDefault("nacionalidadCatalogoId",1));
   var found=db.queryForList("select id_persona, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido from sira.per_persona where id_pais_emisor=? and id_tipo_documento=? and documento_numero=? limit 1",pais,tipo,doc);  boolean existente=!found.isEmpty();
   long pid;
   String pNom = up(p.get("primerNombre"));
   String sNom = up(p.get("segundoNombre"));
   String pApe = up(p.get("primerApellido"));
   String sApe = up(p.get("segundoApellido"));
   if(existente){
    pid=((Number)found.get(0).get("id_persona")).longValue();
    if(pNom==null||pNom.isBlank()) pNom=text(found.get(0).get("primer_nombre"));
    if(sNom==null||sNom.isBlank()) sNom=text(found.get(0).get("segundo_nombre"));
    if(pApe==null||pApe.isBlank()) pApe=text(found.get(0).get("primer_apellido"));
    if(sApe==null||sApe.isBlank()) sApe=text(found.get(0).get("segundo_apellido"));
    if(pNom!=null||pApe!=null){
      db.update("update sira.per_persona set primer_nombre=coalesce(?,primer_nombre), segundo_nombre=coalesce(?,segundo_nombre), primer_apellido=coalesce(?,primer_apellido), segundo_apellido=coalesce(?,segundo_apellido) where id_persona=?",pNom,sNom,pApe,sApe,pid);
    }
   } else {
    pid=db.queryForObject("insert into sira.per_persona(id_pais_emisor,id_tipo_documento,documento_numero,id_nacionalidad_catalogo,id_referido,primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,consentimiento_datos,estado_registro) values(?,?,?,?,?,?,?,?,?,'S','ACTIVO') returning id_persona",Long.class,pais,tipo,doc,nac,row.get("id_referido"),pNom,sNom,pApe,sApe);
   }
   if(!abierta){
    Object amarrada=row.get("id_persona_resultante");
    if(amarrada!=null&&num(amarrada)!=pid) throw new ResponseStatusException(HttpStatus.CONFLICT,"La invitación dirigida corresponde a otra persona");
    db.update("update sira.inv_invitacion set id_persona_resultante=? where id_invitacion=?",pid,iid);
   }
   long uso=db.queryForObject("insert into sira.inv_invitacion_persona(id_invitacion,id_persona,persona_existente,otp_pendiente) values(?,?,?,?) on conflict(id_invitacion,id_persona) do update set fecha_ultimo_acceso=clock_timestamp() returning id_invitacion_persona",Long.class,iid,pid,existente?"S":"N",existente?"S":"N");
   guardarContacto(pid,p);

   String uRef = text(p.get("ubicacionReferencia")!=null?p.get("ubicacionReferencia"):p.get("ubicacion"));
   guardarUbicacion(pid, uRef);

   Map<String,Object> cne;
   try {
    cne=electoral.consultar(pid,Map.of("nacionalidad","V"));
   } catch(Throwable e){
    System.out.println(">>> Error en electoral.consultar: " + e.getMessage());
    e.printStackTrace(System.out);
    cne=Map.of("errorProveedor",String.valueOf(e.getMessage()));
   }

   String cp=text(p.get("codigoPais")), ca=text(p.get("codigoArea")), n=digits(p.get("numero")), m=text(p.get("correo"));
   String tel = (cp.isBlank()?"+58":cp) + " " + (ca.isBlank()?"":ca) + (n.isBlank()?"":" "+n);
   Map<String,Object> out=new LinkedHashMap<>();
   out.put("personaId",pid);
   out.put("usoId",uso);
   out.put("personaExistente",existente);
   out.put("otpPendiente",existente);
   out.put("invitacionAbierta",abierta);
   out.put("documentoNumero",doc);
   out.put("primerNombre",pNom);
   out.put("segundoNombre",sNom);
   out.put("primerApellido",pApe);
   out.put("segundoApellido",sApe);
   out.put("telefono",tel.trim());
   out.put("correo",m);
   out.put("ubicacionReferencia",uRef);
   out.put("electoral",cne);
   return out;
  } catch(Throwable t) {
   System.out.println(">>> Error en confirmar: " + t.getMessage());
   t.printStackTrace(System.out);
   throw t;
  }
 }

 @PostMapping("/iniciar") @Transactional
 public Map<String,Object> iniciar(@RequestBody Map<String,Object> p){
  return confirmar(p);
 }

 @GetMapping("/{token}/contexto") public Map<String,Object> contexto(@PathVariable String token,@RequestParam long usoId){
  long pid=personaUso(token,usoId);
  Map<String,Object> o=new LinkedHashMap<>();
  Map<String,Object> per=new LinkedHashMap<>(db.queryForMap("select * from sira.per_persona where id_persona=?",pid));
  Object fn=per.get("fecha_nacimiento");
  if(fn!=null){
    String fns=String.valueOf(fn);
    if(fns.contains("T")) fns=fns.split("T")[0];
    if(fns.contains(" ")) fns=fns.split(" ")[0];
    per.put("fecha_nacimiento",fns);
  }
  o.put("persona",per);
  o.put("contactos",contactos(pid));
  o.put("redesSociales",db.queryForList("select tipo,valor from sira.per_persona_red_social where id_persona=? and activo='S'",pid));
  Map<String,Object> res=new LinkedHashMap<>(one("select * from sira.per_persona_residencia where id_persona=? and activo='S' and principal='S' limit 1",pid));
  String ubi=obtenerUbicacion(pid);
  res.put("ubicacion",ubi);
  o.put("residencia",res);
  o.put("ubicacion",ubi);
  o.put("ubicacionReferencia",ubi);
  o.put("academico",one("select * from sira.per_persona_datos_academicos where id_persona=?",pid));
  o.put("electoral",electoral.actual(pid));
  o.put("grupos",db.queryForList("select g.id_grupo,g.descripcion from sira.per_persona_grupo pg join sira.org_grupo g on g.id_grupo=pg.id_grupo where pg.id_persona=? and pg.activo='S'",pid));
  o.put("catalogoGrupos",db.queryForList("select id_grupo,descripcion from sira.org_grupo where activo='S' order by descripcion"));
  try{List<String> og=db.queryForList("select otro_grupo from sira.per_persona_otro_grupo where id_persona=?",String.class,pid);o.put("otroGrupo",og.isEmpty()||og.get(0)==null?"":og.get(0));}catch(Exception e){o.put("otroGrupo","");}
  return o;
 }

 @PutMapping("/{token}/personales") public Map<String,Object> personales(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object>x){
  long pid=personaUso(token,usoId);
  db.update("update sira.per_persona set primer_nombre=?,segundo_nombre=?,primer_apellido=?,segundo_apellido=?,alias_apodo=?,fecha_nacimiento=?,id_nacionalidad_catalogo=?,id_sexo=? where id_persona=?",up(x.get("primerNombre")),up(x.get("segundoNombre")),up(x.get("primerApellido")),up(x.get("segundoApellido")),text(x.get("aliasApodo")),parseFecha(x.get("fechaNacimiento")),nullable(x.get("nacionalidadCatalogoId")),nullable(x.get("sexoId")),pid);
  return ok();
 }

 @PostMapping("/{token}/correo") public Map<String,Object> correo(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object>x){long pid=personaUso(token,usoId);String c=text(x.get("correo")).toLowerCase();db.update("update sira.per_persona_correo set principal='N' where id_persona=? and principal='S'",pid);db.update("insert into sira.per_persona_correo(id_persona,correo,principal,verificado,activo) values(?,?,'S','N','S') on conflict do nothing",pid,c);return ok();}
 @PostMapping("/{token}/telefono") public Map<String,Object> telefono(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object>x){
  long pid=personaUso(token,usoId);
  String n=digits(x.get("numero"));
  String pais = text(x.get("codigoPais"));
  if(pais.isBlank()) pais = "+58";
  if("+58".equals(pais)){
      if(n.length()!=7) bad("El teléfono debe tener 7 dígitos");
  } else {
      if(n.length()<6 || n.length()>12) bad("El teléfono internacional debe tener entre 6 y 12 dígitos");
  }
  Long codId = null;
  if(x.get("codigoTelefonicoId")!=null&&!String.valueOf(x.get("codigoTelefonicoId")).isBlank()&&!"0".equals(String.valueOf(x.get("codigoTelefonicoId")))){
      codId = num(x.get("codigoTelefonicoId"));
  } else if(x.get("codigoArea")!=null&&!String.valueOf(x.get("codigoArea")).isBlank()){
      codId = codigoService.resolverOCrear(pais, text(x.get("codigoArea")));
  }
  if(codId==null) bad("Código de área requerido");
  db.update("update sira.per_persona_telefono set principal='N' where id_persona=? and principal='S'",pid);
  db.update("insert into sira.per_persona_telefono(id_persona,id_tipo_contacto,id_codigo_telefonico,numero,extension,principal,usa_whatsapp,verificado,activo) select ?,?,?,?,?, 'S',?,'N','S' where not exists(select 1 from sira.per_persona_telefono where id_persona=? and id_codigo_telefonico=? and numero=? and activo='S')",pid,num(x.get("tipoContactoId")),codId,n,text(x.get("extension")),Boolean.TRUE.equals(x.get("usaWhatsapp"))?"S":"N",pid,codId,n);
  return ok();
 }
 @DeleteMapping("/{token}/correo/{id}") public Map<String,Object> borrarCorreo(@PathVariable String token,@RequestParam long usoId,@PathVariable long id){long pid=personaUso(token,usoId);db.update("update sira.per_persona_correo set activo='N' where id_persona=? and id_persona_correo=?",pid,id);return ok();}
 @DeleteMapping("/{token}/telefono/{id}") public Map<String,Object> borrarTelefono(@PathVariable String token,@RequestParam long usoId,@PathVariable long id){long pid=personaUso(token,usoId);db.update("update sira.per_persona_telefono set activo='N' where id_persona=? and id_persona_telefono=?",pid,id);return ok();}
 @GetMapping("/{token}/redes-sociales") public List<Map<String,Object>> getRedesSociales(@PathVariable String token,@RequestParam long usoId){long pid=personaUso(token,usoId);return db.queryForList("select id_persona_red,tipo,valor from sira.per_persona_red_social where id_persona=? and activo='S' order by tipo",pid);}
 @PutMapping("/{token}/redes-sociales") public Map<String,Object> putRedesSociales(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object> p){long pid=personaUso(token,usoId);for(String tipo:List.of("FACEBOOK","INSTAGRAM","YOUTUBE","X")){String v=text(p.get(tipo.toLowerCase()));if(v.isBlank()&&"X".equals(tipo)){v=text(p.getOrDefault("xtwitter",p.getOrDefault("xTwitter","")));}if(v.isBlank()){db.update("update sira.per_persona_red_social set activo='N' where id_persona=? and tipo=?",pid,tipo);}else{db.update("insert into sira.per_persona_red_social(id_persona,tipo,valor) values(?,?,?) on conflict(id_persona,tipo) do update set valor=excluded.valor,activo='S'",pid,tipo,v);}}return ok();}
 @PutMapping("/{token}/territorio") public Map<String,Object> territorio(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object>x){
  long pid=personaUso(token,usoId);
  Long pais=nullable(x.get("paisId"));
  if(pais==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El país es obligatorio");
  db.update("update sira.per_persona_residencia set activo='N',principal='N',fecha_hasta=current_date where id_persona=? and activo='S'",pid);
  db.update("insert into sira.per_persona_residencia(id_persona,id_pais,id_estado,id_ciudad,id_municipio,id_parroquia,id_sector_geografico,id_codigo_postal,direccion_complementaria,principal,activo) values(?,?,?,?,?,?,?,?,?,'S','S')",pid,pais,nullable(x.get("estadoId")),nullable(x.get("ciudadId")),nullable(x.get("municipioId")),nullable(x.get("parroquiaId")),nullable(x.get("sectorId")),nullable(x.get("codigoPostalId")),text(x.get("direccionComplementaria")));
  guardarUbicacion(pid, text(x.get("ubicacion")));
  return ok();
 }
 @PutMapping("/{token}/academico") public Map<String,Object> academico(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object>x){
  long pid=personaUso(token,usoId);
  String descPost=text(x.get("descripcionPostgrado"));
  String descDoc=text(x.get("descripcionDoctorado"));
  String descMag=text(x.get("descripcionMagister"));
  String postVal=(!descPost.isBlank())||"S".equals(yn(x.get("postgrado")))?"S":"N";
  String docVal=(!descDoc.isBlank())||"S".equals(yn(x.get("doctorado")))?"S":"N";
  String magVal=(!descMag.isBlank())||"S".equals(yn(x.get("magister")))?"S":"N";
  Long t1=nullable(x.get("titulo1Id")!=null?x.get("titulo1Id"):x.get("tituloId"));
  Long t2=nullable(x.get("titulo2Id")!=null?x.get("titulo2Id"):x.get("id_titulo_2"));
  String tit1=text(x.get("titulo"));
  String tit2=text(x.get("titulo2"));
  if(tit1.isBlank()&&t1!=null){
    var r=db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?",t1);
    if(!r.isEmpty())tit1=String.valueOf(r.get(0).get("descripcion"));
  }
  if(tit2.isBlank()&&t2!=null){
    var r=db.queryForList("select descripcion from sira.cat_titulo_academico where id_titulo=?",t2);
    if(!r.isEmpty())tit2=String.valueOf(r.get(0).get("descripcion"));
  }
  db.update("""
    insert into sira.per_persona_datos_academicos(id_persona,id_nivel,id_especialidad,titulo,postgrado,descripcion_postgrado,doctorado,descripcion_doctorado,magister,descripcion_magister,id_honor,ocupacion_1,ocupacion_2,ocupacion_3,id_disponibilidad,id_modalidad,whatsapp,facebook,instagram,youtube,x_twitter,observacion,fecha_actualizacion,usuario_actualizacion,titulo_2,id_especialidad_2,id_titulo_1,id_titulo_2)
    values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,clock_timestamp(),'PUBLICO_INVITACION',?,?,?,?)
    on conflict(id_persona) do update set id_nivel=excluded.id_nivel,id_especialidad=excluded.id_especialidad,titulo=excluded.titulo,postgrado=excluded.postgrado,descripcion_postgrado=excluded.descripcion_postgrado,doctorado=excluded.doctorado,descripcion_doctorado=excluded.descripcion_doctorado,magister=excluded.magister,descripcion_magister=excluded.descripcion_magister,id_honor=excluded.id_honor,ocupacion_1=excluded.ocupacion_1,ocupacion_2=excluded.ocupacion_2,ocupacion_3=excluded.ocupacion_3,id_disponibilidad=excluded.id_disponibilidad,id_modalidad=excluded.id_modalidad,whatsapp=excluded.whatsapp,facebook=excluded.facebook,instagram=excluded.instagram,youtube=excluded.youtube,x_twitter=excluded.x_twitter,observacion=excluded.observacion,fecha_actualizacion=clock_timestamp(),usuario_actualizacion='PUBLICO_INVITACION',titulo_2=excluded.titulo_2,id_especialidad_2=excluded.id_especialidad_2,id_titulo_1=excluded.id_titulo_1,id_titulo_2=excluded.id_titulo_2
  """,pid,nullable(x.get("nivelId")),nullable(x.get("especialidadId")),tit1,postVal,descPost,docVal,descDoc,magVal,descMag,nullable(x.get("honorId")),text(x.get("ocupacion1")),text(x.get("ocupacion2")),text(x.get("ocupacion3")),nullable(x.get("disponibilidadId")),nullable(x.get("modalidadId")),text(x.get("whatsapp")),text(x.get("facebook")),text(x.get("instagram")),text(x.get("youtube")),text(x.get("xTwitter")),text(x.get("observacion")),tit2,nullable(x.get("especialidad2Id")),t1,t2);
  return ok();
 }
 @PostMapping("/{token}/grupos/{gid}") public Map<String,Object> grupo(@PathVariable String token,@RequestParam long usoId,@PathVariable long gid){long pid=personaUso(token,usoId);db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",pid,gid);return ok();}
 @PostMapping("/{token}/grupos") public Map<String,Object> gruposGeneral(@PathVariable String token,@RequestParam long usoId,@RequestBody Map<String,Object> x){
  long pid=personaUso(token,usoId);
  if(x.containsKey("otroGrupo")){
    String og=text(x.get("otroGrupo")).toUpperCase(Locale.ROOT);
    if(!og.isBlank()){
      try {
        db.update("insert into sira.per_persona_otro_grupo(id_persona,otro_grupo) values(?,?) on conflict(id_persona) do update set otro_grupo=excluded.otro_grupo",pid,og);
      } catch(Exception ignored){}
      try {
        Long gid=db.queryForObject("insert into sira.org_grupo(descripcion,activo) values(?,'S') on conflict(descripcion) do update set activo='S' returning id_grupo",Long.class,og);
        if(gid!=null){
          db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",pid,gid);
        }
      } catch(Exception ignored){}
    }
  }
  if(x.containsKey("grupoId")&&x.get("grupoId")!=null&&!String.valueOf(x.get("grupoId")).isBlank()){
    long gid=num(x.get("grupoId"));
    db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",pid,gid);
  }
  return ok();
 }
 @DeleteMapping("/{token}/grupos/{gid}") public Map<String,Object> quitar(@PathVariable String token,@RequestParam long usoId,@PathVariable long gid){long pid=personaUso(token,usoId);db.update("update sira.per_persona_grupo set activo='N',fecha_hasta=clock_timestamp() where id_persona=? and id_grupo=?",pid,gid);return ok();}
 @PostMapping("/{token}/finalizar") public Map<String,Object> finalizar(@PathVariable String token,@RequestParam long usoId){long pid=personaUso(token,usoId);db.update("update sira.inv_invitacion_persona set estado='REGISTRADO',fecha_finalizacion=clock_timestamp() where id_invitacion_persona=?",usoId);Integer ok=db.queryForObject("select case when p.primer_nombre is not null and p.primer_apellido is not null and exists(select 1 from sira.per_persona_telefono t where t.id_persona=p.id_persona and t.activo='S') and exists(select 1 from sira.per_persona_correo c where c.id_persona=p.id_persona and c.activo='S') then 1 else 0 end from sira.per_persona p where p.id_persona=?",Integer.class,pid);if(ok!=null&&ok==1)db.update("update sira.per_persona set registro_fase1_completo='S' where id_persona=?",pid);return Map.of("finalizado",true,"fase1Completa",ok!=null&&ok==1);}

 private long personaUso(String token,long uso){Map<String,Object> inv=service.validar(Map.of("token",token));var r=db.queryForList("select ip.id_persona from sira.inv_invitacion_persona ip where ip.id_invitacion_persona=? and ip.id_invitacion=? and ip.estado in ('ABIERTO','REGISTRADO')",uso,inv.get("id_invitacion"));if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registro de invitación no válido");db.update("update sira.inv_invitacion_persona set fecha_ultimo_acceso=clock_timestamp() where id_invitacion_persona=?",uso);return num(r.get(0).get("id_persona"));}

 private void guardarContacto(long pid,Map<String,Object>p){
  Object c=p.get("codigoTelefonicoId");
  String cp=text(p.get("codigoPais")), ca=text(p.get("codigoArea"));
  Long codId = null;
  if(c!=null&&!String.valueOf(c).isBlank()&&!"0".equals(String.valueOf(c))){
      codId = num(c);
  } else if(!ca.isBlank()){
      codId = codigoService.resolverOCrear(cp.isBlank()?"+58":cp, ca);
  }
  String n=digits(p.get("numero")),m=text(p.get("correo"));
  if(codId!=null&&!n.isBlank()){
      Long t=db.queryForObject("select id_tipo_contacto from sira.cat_tipo_contacto where activo='S' order by case when upper(codigo) like 'CELULAR%' then 0 else 1 end,id_tipo_contacto limit 1",Long.class);
      db.update("update sira.per_persona_telefono set principal='N' where id_persona=? and principal='S'",pid);
      db.update("update sira.per_persona_telefono set activo='N', principal='N' where id_codigo_telefonico=? and numero=? and id_persona!=?",codId,n,pid);
      int updated = db.update("update sira.per_persona_telefono set id_tipo_contacto=?, principal='S', usa_whatsapp='S', activo='S' where id_persona=? and id_codigo_telefonico=? and numero=?",t,pid,codId,n);
      if(updated == 0){
          db.update("insert into sira.per_persona_telefono(id_persona,id_tipo_contacto,id_codigo_telefonico,numero,principal,usa_whatsapp,verificado,activo) values(?,?,?,?,'S','S','N','S')",pid,t,codId,n);
      }
  }
  if(m!=null&&!m.isBlank()){
      db.update("update sira.per_persona_correo set principal='N' where id_persona=? and principal='S'",pid);
      db.update("update sira.per_persona_correo set activo='N', principal='N' where lower(correo)=lower(?) and id_persona!=?",m,pid);
      int updated = db.update("update sira.per_persona_correo set principal='S', activo='S' where id_persona=? and lower(correo)=lower(?)",pid,m);
      if(updated == 0){
          db.update("insert into sira.per_persona_correo(id_persona,correo,principal,verificado,activo) values(?,lower(?),'S','N','S')",pid,m);
      }
  }
 }

 private void guardarUbicacion(long pid, String ubi){
  if(ubi==null||ubi.isBlank()) return;
  try {
      db.update("update sira.per_persona_geolocalizacion set eliminada_usuario='S' where id_persona=? and eliminada_usuario='N'", pid);
      db.update("insert into sira.per_persona_geolocalizacion(id_persona, metodo, resultado_captura, motivo_no_captura, consentimiento, eliminada_usuario) values(?, 'MANUAL', 'NO_DISPONIBLE', ?, 'S', 'N')", pid, ubi.trim());
  } catch(Exception ignored){}
 }

 private String obtenerUbicacion(long pid){
  try {
      var list = db.queryForList("select motivo_no_captura from sira.per_persona_geolocalizacion where id_persona=? and eliminada_usuario='N' order by id_geolocalizacion desc limit 1", pid);
      if(!list.isEmpty() && list.get(0).get("motivo_no_captura") != null){
          return String.valueOf(list.get(0).get("motivo_no_captura"));
      }
  } catch(Exception ignored){}
  return "";
 }

 private java.sql.Date parseFecha(Object v){
  if(v==null) return null;
  String s=String.valueOf(v).trim();
  if(s.isBlank()||"null".equalsIgnoreCase(s)) return null;
  if(s.contains("T")) s=s.split("T")[0].trim();
  if(s.contains(" ")) s=s.split(" ")[0].trim();
  try {
      if(s.matches("^\\d{4}-\\d{2}-\\d{2}$")){
          return java.sql.Date.valueOf(s);
      }
      if(s.matches("^\\d{2}/\\d{2}/\\d{4}$")){
          String[] p=s.split("/");
          return java.sql.Date.valueOf(p[2]+"-"+p[1]+"-"+p[0]);
      }
  } catch(Exception ignored){}
  return null;
 }

 private Map<String,Object> contactos(long p){Map<String,Object>x=new LinkedHashMap<>();x.put("correos",db.queryForList("select * from sira.per_persona_correo where id_persona=? and activo='S'",p));x.put("telefonos",db.queryForList("select t.*,c.codigo_pais,c.codigo_area_operador from sira.per_persona_telefono t join sira.cat_codigo_telefonico c on c.id_codigo_telefonico=t.id_codigo_telefonico where t.id_persona=? and t.activo='S'",p));return x;}
 private Map<String,Object> one(String q,long p){var r=db.queryForList(q,p);return r.isEmpty()?Map.of():r.get(0);}private Map<String,Object> ok(){return Map.of("guardado",true);}private void bad(String x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x);}private long num(Object x){return Long.parseLong(String.valueOf(x));}private Long nullable(Object x){try{String s=String.valueOf(x);return s.isBlank()||"None".equals(s)?null:Long.valueOf(s);}catch(Exception e){return null;}}private String text(Object x){return x==null?"":String.valueOf(x).trim();}private String digits(Object x){return text(x).replaceAll("\\D","");}private String up(Object x){String s=text(x);return s.isBlank()?null:s.toUpperCase();}private String yn(Object x){return Boolean.TRUE.equals(x)?"S":"N";}
}
