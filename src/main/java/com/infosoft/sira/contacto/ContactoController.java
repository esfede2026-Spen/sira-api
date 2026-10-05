package com.infosoft.sira.contacto;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/personas/{personaId}/contactos")
public class ContactoController {
    private final JdbcTemplate db;
    public ContactoController(JdbcTemplate db){this.db=db;}

    @GetMapping
    public Map<String,Object> consultar(@PathVariable long personaId){
        var correos=db.queryForList("select * from sira.per_persona_correo where id_persona=? and activo='S' order by principal desc,id_persona_correo",personaId);
        var telefonos=db.queryForList("""
            select t.*,tc.codigo tipo_contacto,tc.descripcion desc_tipo_contacto,
                   c.codigo_pais,c.codigo_area_operador,c.descripcion desc_codigo
              from sira.per_persona_telefono t
              join sira.cat_tipo_contacto tc on tc.id_tipo_contacto=t.id_tipo_contacto
              join sira.cat_codigo_telefonico c on c.id_codigo_telefonico=t.id_codigo_telefonico
             where t.id_persona=? and t.activo='S'
             order by t.principal desc,t.id_persona_telefono
            """,personaId);
        return Map.of("correos",correos,"telefonos",telefonos);
    }

    @PostMapping("/correo") @Transactional
    public Map<String,Object> correo(@PathVariable long personaId,@RequestBody Map<String,Object> p){
        String correo=req(p,"correo").toLowerCase(Locale.ROOT).trim();
        boolean principal=bool(p.get("principal"));
        if(principal) db.update("update sira.per_persona_correo set principal='N' where id_persona=?",personaId);
        Long id=db.queryForObject("""
            insert into sira.per_persona_correo(id_persona,correo,principal,verificado,activo)
            values(?,?,?,'N','S') returning id_persona_correo
            """,Long.class,personaId,correo,principal?"S":"N");
        return Map.of("idPersonaCorreo",Objects.requireNonNull(id));
    }

    @PostMapping("/telefono") @Transactional
    public Map<String,Object> telefono(@PathVariable long personaId,@RequestBody Map<String,Object> p){
        long tipo=num(p,"tipoContactoId"),codigo=num(p,"codigoTelefonicoId");
        String numero=req(p,"numero").replaceAll("[^0-9]","");
        if(!numero.matches("\\d{7}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El número debe contener exactamente 7 dígitos");
        boolean principal=bool(p.get("principal"));
        if(principal) db.update("update sira.per_persona_telefono set principal='N' where id_persona=?",personaId);
        Long id=db.queryForObject("""
            insert into sira.per_persona_telefono(id_persona,id_tipo_contacto,id_codigo_telefonico,numero,extension,principal,usa_whatsapp,verificado,activo)
            values(?,?,?,?,?, ?,?,'N','S') returning id_persona_telefono
            """,Long.class,personaId,tipo,codigo,numero,text(p.get("extension")),principal?"S":"N",bool(p.get("usaWhatsapp"))?"S":"N");
        return Map.of("idPersonaTelefono",Objects.requireNonNull(id));
    }

    @DeleteMapping("/correo/{id}") public Map<String,Object> borrarCorreo(@PathVariable long personaId,@PathVariable long id){db.update("update sira.per_persona_correo set activo='N',principal='N' where id_persona=? and id_persona_correo=?",personaId,id);return Map.of("eliminado",true);}
    @DeleteMapping("/telefono/{id}") public Map<String,Object> borrarTelefono(@PathVariable long personaId,@PathVariable long id){db.update("update sira.per_persona_telefono set activo='N',principal='N' where id_persona=? and id_persona_telefono=?",personaId,id);return Map.of("eliminado",true);}

    private String req(Map<String,Object> p,String k){String v=text(p.get(k));if(v==null||v.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta "+k);return v;}
    private String text(Object v){return v==null?null:String.valueOf(v).trim();}
    private long num(Map<String,Object> p,String k){try{return Long.parseLong(req(p,k));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor inválido: "+k);}}
    private boolean bool(Object v){return Boolean.TRUE.equals(v)||"true".equalsIgnoreCase(String.valueOf(v))||"S".equalsIgnoreCase(String.valueOf(v));}
}
