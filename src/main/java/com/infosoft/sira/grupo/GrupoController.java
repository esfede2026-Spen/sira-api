package com.infosoft.sira.grupo;
import org.springframework.http.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@RestController @RequestMapping("/api/v1") public class GrupoController{private final JdbcTemplate db;public GrupoController(JdbcTemplate db){this.db=db;}
@GetMapping("/grupos") public List<Map<String,Object>> grupos(){return db.queryForList("select id_grupo,descripcion,activo from sira.org_grupo order by activo desc,descripcion");}
@PostMapping("/grupos") public Map<String,Object> crear(@RequestBody Map<String,Object> p){String d=req(p);Long id=db.queryForObject("insert into sira.org_grupo(descripcion,activo) values(?,'S') on conflict(descripcion) do update set activo='S' returning id_grupo",Long.class,d);return Map.of("idGrupo",id);}
@PutMapping("/grupos/{id}") public Map<String,Object> editar(@PathVariable long id,@RequestBody Map<String,Object> p){if(db.update("update sira.org_grupo set descripcion=? where id_grupo=?",req(p),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Grupo no encontrado");return Map.of("guardado",true);}
@DeleteMapping("/grupos/{id}") public Map<String,Object> eliminar(@PathVariable long id){Integer usados=db.queryForObject("select count(*) from sira.per_persona_grupo where id_grupo=? and activo='S'",Integer.class,id);if(usados!=null&&usados>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede eliminar: el grupo tiene personas asociadas");db.update("update sira.org_grupo set activo='N' where id_grupo=?",id);return Map.of("eliminado",true);}
@GetMapping("/personas/{id}/grupos") public List<Map<String,Object>> persona(@PathVariable long id){return db.queryForList("select g.id_grupo,g.descripcion,pg.fecha_desde from sira.per_persona_grupo pg join sira.org_grupo g on g.id_grupo=pg.id_grupo where pg.id_persona=? and pg.activo='S' order by g.descripcion",id);}
@PostMapping("/personas/{id}/grupos/{gid}") public Map<String,Object> agregar(@PathVariable long id,@PathVariable long gid){db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",id,gid);return Map.of("guardado",true);}
@PostMapping("/personas/{id}/grupos") public Map<String,Object> guardar(@PathVariable long id,@RequestBody Map<String,Object> p){
    if(p.containsKey("otroGrupo")){
        String og=String.valueOf(p.getOrDefault("otroGrupo","")).trim().toUpperCase(Locale.ROOT);
        if(!og.isBlank()){
            try {
                db.update("insert into sira.per_persona_otro_grupo(id_persona,otro_grupo) values(?,?) on conflict(id_persona) do update set otro_grupo=excluded.otro_grupo",id,og);
            } catch(Exception ignored){}
            try {
                Long gid=db.queryForObject("insert into sira.org_grupo(descripcion,activo) values(?,'S') on conflict(descripcion) do update set activo='S' returning id_grupo",Long.class,og);
                if(gid!=null){
                    db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",id,gid);
                }
            } catch(Exception ignored){}
        }
    }
    if(p.containsKey("grupoId")&&p.get("grupoId")!=null&&!String.valueOf(p.get("grupoId")).isBlank()){
        long gid=Long.parseLong(String.valueOf(p.get("grupoId")));
        db.update("insert into sira.per_persona_grupo(id_persona,id_grupo) values(?,?) on conflict(id_persona,id_grupo) do update set activo='S',fecha_hasta=null",id,gid);
    }
    return Map.of("guardado",true);
}
@PutMapping("/personas/{id}/otro-grupo") public Map<String,Object> guardarOtro(@PathVariable long id,@RequestBody Map<String,Object> p){
    String og=String.valueOf(p.getOrDefault("otroGrupo","")).trim().toUpperCase(Locale.ROOT);
    try {
        db.update("insert into sira.per_persona_otro_grupo(id_persona,otro_grupo) values(?,?) on conflict(id_persona) do update set otro_grupo=excluded.otro_grupo",id,og.isBlank()?null:og);
    } catch(Exception ignored){}
    return Map.of("guardado",true,"otroGrupo",og);
}
@GetMapping("/personas/{id}/otro-grupo") public Map<String,Object> consultarOtro(@PathVariable long id){
    try {
        List<String> list=db.queryForList("select otro_grupo from sira.per_persona_otro_grupo where id_persona=?",String.class,id);
        return Map.of("otroGrupo",list.isEmpty()||list.get(0)==null?"":list.get(0));
    } catch(Exception e){
        return Map.of("otroGrupo","");
    }
}
@DeleteMapping("/personas/{id}/grupos/{gid}") public Map<String,Object> quitar(@PathVariable long id,@PathVariable long gid){db.update("update sira.per_persona_grupo set activo='N',fecha_hasta=clock_timestamp() where id_persona=? and id_grupo=?",id,gid);return Map.of("eliminado",true);}private String req(Map<String,Object> p){String d=String.valueOf(p.getOrDefault("descripcion","")).trim();if(d.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La descripción es obligatoria");return d;}}
