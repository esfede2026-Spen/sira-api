package com.infosoft.sira.persona;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/personas")
public class PersonaController {
    private final JdbcTemplate db;
    public PersonaController(JdbcTemplate db) { this.db = db; }

    @GetMapping
    public List<Map<String,Object>> listar() {
        return db.queryForList("""
            select p.id_persona,p.uuid_persona,p.documento_numero,p.primer_nombre,
                   p.segundo_nombre,p.primer_apellido,p.segundo_apellido,p.alias_apodo,
                   p.fecha_nacimiento,p.id_pais_emisor,p.id_tipo_documento,p.id_nacionalidad,
                   p.id_sexo,p.id_referido,p.id_nacionalidad_catalogo,p.consentimiento_datos,p.nivel_confianza,p.estado_registro,
                   p.registro_basico_completo,p.registro_fase1_completo
              from sira.per_persona p
             order by p.id_persona desc
            """);
    }

    @GetMapping("/buscar")
    public Map<String,Object> buscar(@RequestParam String documento,
                                     @RequestParam(defaultValue="1") long paisEmisorId,
                                     @RequestParam(defaultValue="1") int tipoDocumentoId) {
        List<Map<String,Object>> rows=db.queryForList("""
            select * from sira.per_persona
             where id_pais_emisor=? and id_tipo_documento=? and documento_numero=?
             limit 1
            """,paisEmisorId,tipoDocumentoId,documento.trim());
        return rows.isEmpty()?Map.of("existe",false):new LinkedHashMap<>(){{put("existe",true);put("persona",rows.get(0));}};
    }

    @GetMapping("/{id}")
    public Map<String,Object> consultar(@PathVariable long id) {
        List<Map<String,Object>> rows=db.queryForList("select * from sira.per_persona where id_persona=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Persona no encontrada");
        return rows.get(0);
    }

    @PostMapping
    @Transactional
    public Map<String,Object> crear(@Valid @RequestBody PersonaRequest r) {
        try {
            Long id=db.queryForObject("""
                insert into sira.per_persona(
                    uuid_persona,id_pais_emisor,id_tipo_documento,documento_numero,
                    primer_nombre,segundo_nombre,primer_apellido,segundo_apellido,
                    alias_apodo,fecha_nacimiento,id_nacionalidad,id_sexo,id_referido,id_nacionalidad_catalogo,consentimiento_datos,
                    nivel_confianza,estado_registro,registro_basico_completo,registro_fase1_completo)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,'ACTIVO','N','N') returning id_persona
                """,Long.class,UUID.randomUUID(),nvl(r.paisEmisorId(),1),nvl(r.tipoDocumentoId(),1),
                limpio(r.documentoNumero()),may(r.primerNombre()),may(r.segundoNombre()),may(r.primerApellido()),
                may(r.segundoApellido()),limpio(r.aliasApodo()),r.fechaNacimiento(),r.nacionalidadId(),r.sexoId(),r.referidoId(),r.nacionalidadCatalogoId(),Boolean.TRUE.equals(r.consentimientoDatos())?"S":"N");
            return Map.of("idPersona",Objects.requireNonNull(id),"estadoRegistro","ACTIVO");
        } catch(DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Ya existe una persona con ese documento");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String,Object> actualizar(@PathVariable long id,@Valid @RequestBody PersonaRequest r) {
        int n=db.update("""
            update sira.per_persona set
              id_pais_emisor=?,id_tipo_documento=?,documento_numero=?,primer_nombre=?,segundo_nombre=?,
              primer_apellido=?,segundo_apellido=?,alias_apodo=?,fecha_nacimiento=?,id_nacionalidad=?,
              id_sexo=?,id_referido=?,id_nacionalidad_catalogo=?,consentimiento_datos=?,registro_basico_completo='S',usuario_actualizacion=current_user
            where id_persona=?
            """,nvl(r.paisEmisorId(),1),nvl(r.tipoDocumentoId(),1),limpio(r.documentoNumero()),
            may(r.primerNombre()),may(r.segundoNombre()),may(r.primerApellido()),may(r.segundoApellido()),
            limpio(r.aliasApodo()),r.fechaNacimiento(),r.nacionalidadId(),r.sexoId(),r.referidoId(),r.nacionalidadCatalogoId(),Boolean.TRUE.equals(r.consentimientoDatos())?"S":"N",id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Persona no encontrada");
        return Map.of("actualizado",true,"idPersona",id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String,Object> eliminarPrueba(@PathVariable long id) {
        Integer admin=db.queryForObject("select count(*) from sira.seg_usuario where id_persona=? and lower(username)='admin'",Integer.class,id);
        if(admin!=null&&admin>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede eliminar la persona administradora");
        Integer usuarios=db.queryForObject("select count(*) from sira.seg_usuario where id_persona=?",Integer.class,id);
        if(usuarios!=null&&usuarios>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Elimine primero la cuenta de usuario asociada");
        Integer referidos=db.queryForObject("select count(*) from sira.per_persona where id_referido=?",Integer.class,id);
        if(referidos!=null&&referidos>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"La persona tiene personas referidas y no puede eliminarse");
        db.update("delete from sira.per_persona_geolocalizacion where id_persona=?",id);
        db.update("delete from sira.per_persona_residencia where id_persona=?",id);
        db.update("delete from sira.per_persona_registro_electoral where id_persona=?",id);
        db.update("delete from sira.ele_consulta where id_persona=?",id);
        db.update("delete from sira.per_persona_telefono where id_persona=?",id);
        db.update("delete from sira.per_persona_correo where id_persona=?",id);
        db.update("delete from sira.seg_validacion_humana where id_persona=?",id);
        db.update("delete from sira.seg_otp where id_persona=?",id);
        db.update("delete from sira.inv_invitacion_evento where id_invitacion in (select id_invitacion from sira.inv_invitacion where id_referido=? or id_persona_resultante=?)",id,id);
        db.update("delete from sira.reg_proceso_paso where id_proceso in (select id_proceso from sira.reg_proceso where id_persona=? or id_referido=?)",id,id);
        db.update("delete from sira.reg_proceso where id_persona=? or id_referido=?",id,id);
        db.update("delete from sira.inv_invitacion where id_referido=? or id_persona_resultante=?",id,id);
        int n=db.update("delete from sira.per_persona where id_persona=?",id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Persona no encontrada");
        return Map.of("eliminado",true,"idPersona",id);
    }

    private int nvl(Integer v,int d){return v==null?d:v;}
    private String limpio(String v){return v==null||v.isBlank()?null:v.trim();}
    private String may(String v){String x=limpio(v);return x==null?null:x.toUpperCase();}
}
