package com.infosoft.sira.electoral;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/electoral")
public class ElectoralController {
 private final JdbcTemplate db; private final RestClient http;
 @Value("${cedula.api.enabled:false}") boolean enabled;
 @Value("${cedula.api.base-url:https://api.cedula.com.ve/api/v1}") String baseUrl;
 @Value("${cedula.api.app-id:}") String appId;
 @Value("${cedula.api.token:}") String token;
 public ElectoralController(JdbcTemplate db){this.db=db;this.http=RestClient.create();}

 @GetMapping("/consultas/persona/{personaId}")
 public List<Map<String,Object>> consultas(@PathVariable long personaId){return db.queryForList("select id_consulta_electoral,id_persona,documento_numero,proveedor,fecha_consulta,resultado,codigo_respuesta,mensaje_respuesta,resultado_persona,resultado_electoral,estado_procesamiento from sira.ele_consulta where id_persona=? order by fecha_consulta desc",personaId);}

 @GetMapping("/persona/{personaId}")
 public Map<String,Object> actual(@PathVariable long personaId){
  var rows=db.queryForList("""
   select r.*,e.descripcion estado,m.descripcion municipio,p.descripcion parroquia,c.nombre centro_electoral,c.direccion centro_direccion
   from sira.per_persona_registro_electoral r
   left join sira.ele_estado e on e.id_estado_electoral=r.id_estado_electoral
   left join sira.ele_municipio m on m.id_municipio_electoral=r.id_municipio_electoral
   left join sira.ele_parroquia p on p.id_parroquia_electoral=r.id_parroquia_electoral
   left join sira.ele_centro_votacion c on c.id_centro_votacion=r.id_centro_votacion
   where r.id_persona=? and r.vigente='S' order by r.fecha_desde desc limit 1
  """,personaId);
  return rows.isEmpty()?Map.of("existe",false):new LinkedHashMap<>(){{put("existe",true);put("registro",rows.get(0));}};
 }

 @PostMapping("/consultas/persona/{personaId}")
 @Transactional
 public Map<String,Object> consultar(@PathVariable long personaId,@RequestBody(required=false) Map<String,Object> input){
  try {
   Map<String,Object> per=persona(personaId); String doc=String.valueOf(per.get("documento_numero"));
   String nac=input==null?"V":String.valueOf(input.getOrDefault("nacionalidad","V")); UUID corr=UUID.randomUUID(); long ini=System.currentTimeMillis();
   if(!enabled||appId.isBlank()||token.isBlank()) return Map.of("errorProveedor","La consulta CNE no está habilitada o faltan variables CEDULA_API_APP_ID/CEDULA_API_TOKEN","indicadorRegistroExistente","N");
   Map<String,Object> response; String error=null;
   try{
    String url=baseUrl+"?app_id="+enc(appId)+"&token="+enc(token)+"&nacionalidad="+enc(nac)+"&cedula="+enc(doc); String body=http.get().uri(URI.create(url)).retrieve().body(String.class); response=body==null?Map.of():new ObjectMapper().readValue(body,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
   }catch(Exception ex){response=Map.of("error",true,"error_str",ex.getMessage()==null?"Error del proveedor":ex.getMessage());error=String.valueOf(response.get("error_str"));}
   boolean ok=response!=null&&!Boolean.TRUE.equals(response.get("error"))&&response.get("data") instanceof Map;
   Map<String,Object> data=ok?(Map<String,Object>)response.get("data"):Map.of(); Map<String,Object> cne=data.get("cne") instanceof Map?(Map<String,Object>)data.get("cne"):Map.of();
   String nombres=join(data.get("primer_nombre"),data.get("segundo_nombre")); String apellidos=join(data.get("primer_apellido"),data.get("segundo_apellido"));
   String estado=text(cne.get("estado")), municipio=text(cne.get("municipio")), parroquia=text(cne.get("parroquia")), centro=text(cne.get("centro_electoral"));
   boolean found=ok&&(!nombres.isBlank()||!apellidos.isBlank()||!estado.isBlank()||!centro.isBlank());
   Long consultaId=db.queryForObject("""
    insert into sira.ele_consulta(id_persona,id_pais_emisor,id_tipo_documento,documento_numero,proveedor,version_servicio,fecha_consulta,resultado,codigo_respuesta,mensaje_respuesta,tiempo_respuesta_ms,respuesta_original,id_correlacion,resultado_persona,resultado_electoral,estado_procesamiento,intentos,error_proveedor,ultima_fecha_intento)
    values(?,?,?,?,?,'v1',clock_timestamp(),?,?,?, ?,?::jsonb,?,?,?,'COMPLETADA',1,?,clock_timestamp()) returning id_consulta_electoral
   """,Long.class,personaId,per.get("id_pais_emisor"),per.get("id_tipo_documento"),doc,"CEDULA_COM_VE",found?"ENCONTRADO":(error==null?"NO_ENCONTRADO":"ERROR"),found?"200":(error==null?"404":"502"),found?"Consulta completada":"Registro no encontrado",(int)(System.currentTimeMillis()-ini),jsonSeguro(response),corr,found?"ENCONTRADA":"NO_ENCONTRADA",found?"DISPONIBLE":"NO_DISPONIBLE",corta(error,60));
   Long est=found&&!estado.isBlank()?estado(estado):null; Long mun=found&&!municipio.isBlank()?municipio(est,municipio):null; Long par=found&&!parroquia.isBlank()?parroquia(mun,parroquia):null; Long cen=found&&!centro.isBlank()?centro(par,centro):null;
   db.update("update sira.per_persona_registro_electoral set vigente='N',fecha_hasta=greatest(clock_timestamp(), fecha_desde + interval '1 millisecond'),fecha_actualizacion=clock_timestamp() where id_persona=? and vigente='S'",personaId);
   db.update("""
    insert into sira.per_persona_registro_electoral(id_persona,id_consulta_electoral,indicador_registro_existente,origen_dato,nombres_fuente,apellidos_fuente,id_estado_electoral,id_municipio_electoral,id_parroquia_electoral,id_centro_votacion,sujeto_revision,vigente,fecha_desde,fecha_creacion)
    values(?,?,?,?,?,?,?,?,?,?,?,'S',clock_timestamp(),clock_timestamp())
   """,personaId,consultaId,found?"S":"N","CONSULTA",empty(nombres),empty(apellidos),est,mun,par,cen,found?"N":"S");
   String[] nomSep = separarCompuesto(nombres);
   String[] apeSep = separarCompuesto(apellidos);
   if(found) {
    db.update("""
     update sira.per_persona set 
       primer_nombre=coalesce(nullif(?,''),primer_nombre),
       segundo_nombre=coalesce(nullif(?,''),segundo_nombre),
       primer_apellido=coalesce(nullif(?,''),primer_apellido),
       segundo_apellido=coalesce(nullif(?,''),segundo_apellido)
     where id_persona=?
    """,empty(nomSep[0]),empty(nomSep[1]),empty(apeSep[0]),empty(apeSep[1]),personaId);
   }
   Map<String,Object> out=vista(personaId,consultaId,found,nombres,apellidos,estado,municipio,parroquia,centro,doc,nomSep[0],nomSep[1],apeSep[0],apeSep[1]); if(error!=null)out.put("errorProveedor",corta(error,240)); return out;
  } catch(Exception e) {
   System.out.println(">>> Error capturado en electoral.consultar: " + e.getMessage());
   return Map.of("errorProveedor", e.getMessage() == null ? "Error en consulta electoral" : e.getMessage(), "indicadorRegistroExistente", "N");
  }
 }

 public Map<String,Object> consultarDirecto(String nac, String doc){
  if(!enabled||appId.isBlank()||token.isBlank()) return Map.of("errorProveedor","La consulta CNE no está habilitada o faltan variables CEDULA_API_APP_ID/CEDULA_API_TOKEN","indicadorRegistroExistente","N");
  Map<String,Object> response; String error=null;
  try{
   String url=baseUrl+"?app_id="+enc(appId)+"&token="+enc(token)+"&nacionalidad="+enc(nac)+"&cedula="+enc(doc);
   String body=http.get().uri(URI.create(url)).retrieve().body(String.class);
   response=body==null?Map.of():new ObjectMapper().readValue(body,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
  }catch(Exception ex){
   response=Map.of("error",true,"error_str",ex.getMessage()==null?"Error del proveedor":ex.getMessage());
   error=String.valueOf(response.get("error_str"));
  }
  boolean ok=response!=null&&!Boolean.TRUE.equals(response.get("error"))&&response.get("data") instanceof Map;
  Map<String,Object> data=ok?(Map<String,Object>)response.get("data"):Map.of();
  Map<String,Object> cne=data.get("cne") instanceof Map?(Map<String,Object>)data.get("cne"):Map.of();
  String nombres=join(data.get("primer_nombre"),data.get("segundo_nombre"));
  String apellidos=join(data.get("primer_apellido"),data.get("segundo_apellido"));
  String estado=text(cne.get("estado")), municipio=text(cne.get("municipio")), parroquia=text(cne.get("parroquia")), centro=text(cne.get("centro_electoral"));
  boolean found=ok&&(!nombres.isBlank()||!apellidos.isBlank()||!estado.isBlank()||!centro.isBlank());
  String[] nomSep = separarCompuesto(nombres);
  String[] apeSep = separarCompuesto(apellidos);
  Map<String,Object> out = new LinkedHashMap<>();
  out.put("encontrado", found);
  out.put("indicadorRegistroExistente", found ? "S" : "N");
  out.put("nombres", nombres);
  out.put("apellidos", apellidos);
  out.put("primerNombre", nomSep[0]);
  out.put("segundoNombre", nomSep[1]);
  out.put("primerApellido", apeSep[0]);
  out.put("segundoApellido", apeSep[1]);
  out.put("estado", estado);
  out.put("municipio", municipio);
  out.put("parroquia", parroquia);
  out.put("centroElectoral", centro);
  out.put("mensajePantalla", "Los datos aquí presentados pudieran estar o no actualizados en relación con la base de datos del CNE.");
  out.put("datosElectorales", Map.of("estado", estado, "municipio", municipio, "parroquia", parroquia, "centroElectoral", centro));
  if(error != null) out.put("errorProveedor", corta(error, 240));
  return out;
 }

 @PostMapping("/persona/{personaId}/declaracion") @Transactional
 public Map<String,Object> declaracion(@PathVariable long personaId,@RequestBody Map<String,Object> p){
  var actual=actual(personaId); if(!(Boolean)actual.get("existe"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Primero debe realizar la consulta electoral");
  Map<String,Object> r=(Map<String,Object>)actual.get("registro"); long rid=((Number)r.get("id_persona_registro_electoral")).longValue();
  db.update("update sira.per_persona_registro_electoral set inscrito_declarado=?,observacion=?,sujeto_revision='S',origen_dato='DECLARADO',fecha_actualizacion=clock_timestamp() where id_persona_registro_electoral=?",text(p.get("inscritoDeclarado")),text(p.get("observacion")),rid);
  return Map.of("actualizado",true,"idPersonaRegistroElectoral",rid);
 }

 private Map<String,Object> persona(long id){var r=db.queryForList("select * from sira.per_persona where id_persona=?",id);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Persona no encontrada");return r.get(0);}
 private Long estado(String v){String n=norm(v);var r=db.queryForList("select id_estado_electoral from sira.ele_estado where descripcion_normalizada=? limit 1",n);if(!r.isEmpty())return ((Number)r.get(0).get("id_estado_electoral")).longValue();return db.queryForObject("insert into sira.ele_estado(descripcion,descripcion_normalizada,activo,fecha_creacion) values(?,?,'S',clock_timestamp()) returning id_estado_electoral",Long.class,v,n);}
 private Long municipio(Long e,String v){String n=norm(v);var r=db.queryForList("select id_municipio_electoral from sira.ele_municipio where id_estado_electoral=? and descripcion_normalizada=? limit 1",e,n);if(!r.isEmpty())return ((Number)r.get(0).get("id_municipio_electoral")).longValue();return db.queryForObject("insert into sira.ele_municipio(id_estado_electoral,descripcion,descripcion_normalizada,activo,fecha_creacion) values(?,?,?,'S',clock_timestamp()) returning id_municipio_electoral",Long.class,e,v,n);}
 private Long parroquia(Long m,String v){String n=norm(v);var r=db.queryForList("select id_parroquia_electoral from sira.ele_parroquia where id_municipio_electoral=? and descripcion_normalizada=? limit 1",m,n);if(!r.isEmpty())return ((Number)r.get(0).get("id_parroquia_electoral")).longValue();return db.queryForObject("insert into sira.ele_parroquia(id_municipio_electoral,descripcion,descripcion_normalizada,activo,fecha_creacion) values(?,?,?,'S',clock_timestamp()) returning id_parroquia_electoral",Long.class,m,v,n);}
 private Long centro(Long p,String v){String n=norm(v);var r=db.queryForList("select id_centro_votacion from sira.ele_centro_votacion where id_parroquia_electoral=? and nombre_normalizado=? limit 1",p,n);if(!r.isEmpty())return ((Number)r.get(0).get("id_centro_votacion")).longValue();return db.queryForObject("insert into sira.ele_centro_votacion(id_parroquia_electoral,nombre,nombre_normalizado,activo,fecha_creacion) values(?,?,?,'S',clock_timestamp()) returning id_centro_votacion",Long.class,p,v,n);}
 private Map<String,Object> vista(long pid,long cid,boolean f,String nom,String ape,String e,String m,String p,String c,String doc,String nom1,String nom2,String ape1,String ape2){
  Map<String,Object>x=new LinkedHashMap<>();
  x.put("personaId",pid);
  x.put("consultaId",cid);
  x.put("documentoConsultado",doc);
  x.put("indicadorRegistroExistente",f?"S":"N");
  x.put("nombres",nom);
  x.put("apellidos",ape);
  x.put("primerNombre",nom1);
  x.put("segundoNombre",nom2);
  x.put("primerApellido",ape1);
  x.put("segundoApellido",ape2);
  x.put("mensajePantalla","Los datos aquí presentados pudieran estar o no actualizados en relación con la base de datos del CNE.");
  x.put("datosElectorales",Map.of("estado",e,"municipio",m,"parroquia",p,"centroElectoral",c));
  return x;
 }
 private String jsonSeguro(Object o){ return "{}"; }
 private String corta(String v,int n){return v==null?null:(v.length()<=n?v:v.substring(0,n));}
 private String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}
 private String norm(String v){return Normalizer.normalize(text(v),Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
 private String text(Object v){return v==null?"":String.valueOf(v).trim();} 
 private String empty(String v){return v==null||v.isBlank()?null:v.trim();} 
 private String join(Object a,Object b){return (text(a)+" "+text(b)).trim();} 
 private String first(String v){return v==null||v.isBlank()?"":v.trim().split("\\s+")[0];}

 public static String[] separarCompuesto(String s) {
  if (s == null || s.isBlank()) return new String[]{"", ""};
  String[] raw = s.trim().split("\\s+");
  List<String> units = new ArrayList<>();
  Set<String> particles = Set.of(
    "DE", "DEL", "LA", "LAS", "LOS", "SAN", "SANTA", "SANTO", "DA", "DAS", "DO", "DOS", "VON", "VAN", "MC", "MAC", "AL", "EL"
  );
  StringBuilder cur = new StringBuilder();
  for (int i = 0; i < raw.length; i++) {
    String token = raw[i].trim();
    if (token.isEmpty()) continue;
    if (cur.length() > 0) cur.append(" ");
    cur.append(token);
    if (particles.contains(token.toUpperCase(Locale.ROOT)) && i < raw.length - 1) {
      continue;
    }
    units.add(cur.toString());
    cur.setLength(0);
  }
  if (cur.length() > 0) units.add(cur.toString());
  if (units.isEmpty()) return new String[]{"", ""};
  if (units.size() == 1) return new String[]{units.get(0), ""};
  if (units.size() == 2) return new String[]{units.get(0), units.get(1)};
  String p1 = units.get(0);
  String p2 = String.join(" ", units.subList(1, units.size()));
  return new String[]{p1, p2};
 }
}
