package com.infosoft.sira.geografia;
import com.infosoft.sira.contacto.CodigoTelefonicoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/geografia")
public class GeografiaController{
    private final JdbcTemplate db;
    private final CodigoTelefonicoService codigoService;

    public GeografiaController(JdbcTemplate db, CodigoTelefonicoService codigoService){
        this.db = db;
        this.codigoService = codigoService;
    }

    @GetMapping("/paises") public List<Map<String,Object>> paises(){return db.queryForList("select id_pais,codigo_iso2,desc_pais from sira.pais where activo='S' order by case when codigo_iso2='VE' then 0 else 1 end,desc_pais");}
    @GetMapping("/estados") public List<Map<String,Object>> estados(@RequestParam long paisId){return db.queryForList("select id_estado,desc_estado from sira.estado where activo='S' and id_pais=? order by desc_estado",paisId);}
    @GetMapping("/ciudades") public List<Map<String,Object>> ciudades(@RequestParam long estadoId){return db.queryForList("select id_ciudad,desc_ciudad from sira.ciudad where activo='S' and id_estado=? order by desc_ciudad",estadoId);}
    @GetMapping("/municipios") public List<Map<String,Object>> municipios(@RequestParam(required=false) Long ciudadId,@RequestParam(required=false) Long estadoId){return ciudadId!=null?db.queryForList("select id_municipio,desc_municipio from sira.municipio where activo='S' and id_ciudad=? order by desc_municipio",ciudadId):db.queryForList("select distinct m.id_municipio,m.desc_municipio from sira.municipio m left join sira.ciudad c on c.id_ciudad=m.id_ciudad where m.activo='S' and c.id_estado=? order by m.desc_municipio",estadoId);}
    @GetMapping("/parroquias") public List<Map<String,Object>> parroquias(@RequestParam long municipioId){return db.queryForList("select id_parroquia,desc_parroquia from sira.parroquia where activo='S' and id_municipio=? order by desc_parroquia",municipioId);}
    @GetMapping("/tipos-sector") public List<Map<String,Object>> tipos(){return db.queryForList("select id_tipo_sector_geografico,desc_tipo_sector_geografico from sira.tipo_sector_geografico where activo='S' order by desc_tipo_sector_geografico");}
    @GetMapping("/sectores")
    public List<Map<String,Object>> sectores(@RequestParam long estadoId,
                                            @RequestParam(required=false) Long ciudadId,
                                            @RequestParam(required=false) Long municipioId,
                                            @RequestParam(required=false) Long parroquiaId,
                                            @RequestParam(required=false) Long tipoSectorId) {
        if (parroquiaId != null) {
            var list = db.queryForList("select id_sector_geografico,id_tipo_sector_geografico,desc_sector_geografico from sira.sector_geografico where activo='S' and id_parroquia=? and (? is null or id_tipo_sector_geografico=?) order by desc_sector_geografico", parroquiaId, tipoSectorId, tipoSectorId);
            if (!list.isEmpty()) return list;
        }
        if (municipioId != null) {
            var list = db.queryForList("select id_sector_geografico,id_tipo_sector_geografico,desc_sector_geografico from sira.sector_geografico where activo='S' and id_municipio=? and (? is null or id_tipo_sector_geografico=?) order by desc_sector_geografico", municipioId, tipoSectorId, tipoSectorId);
            if (!list.isEmpty()) return list;
        }
        if (ciudadId != null) {
            var list = db.queryForList("select id_sector_geografico,id_tipo_sector_geografico,desc_sector_geografico from sira.sector_geografico where activo='S' and id_ciudad=? and (? is null or id_tipo_sector_geografico=?) order by desc_sector_geografico", ciudadId, tipoSectorId, tipoSectorId);
            if (!list.isEmpty()) return list;
        }
        return db.queryForList("select id_sector_geografico,id_tipo_sector_geografico,desc_sector_geografico from sira.sector_geografico where activo='S' and id_estado=? and (? is null or id_tipo_sector_geografico=?) and id_parroquia is null and id_municipio is null order by desc_sector_geografico", estadoId, tipoSectorId, tipoSectorId);
    }
    @GetMapping("/codigos-postales") public List<Map<String,Object>> cp(@RequestParam long sectorId){return db.queryForList("select id_codigo_postal,codigo_postal from sira.codigo_postal where activo='S' and id_sector_geografico=? order by codigo_postal",sectorId);}
    @GetMapping("/codigos-telefonicos") public List<Map<String,Object>> codigos(){return codigoService.listar();}
    @PostMapping("/codigos-telefonicos/resolver") public Map<String,Object> resolver(@RequestBody Map<String,Object> p){
        String cp = String.valueOf(p.getOrDefault("codigoPais", "+58"));
        String ca = String.valueOf(p.getOrDefault("codigoArea", ""));
        Long id = codigoService.resolverOCrear(cp, ca);
        return Map.of("idCodigoTelefonico", id, "codigoPais", cp, "codigoArea", ca);
    }
    @GetMapping("/tipos-contacto") public List<Map<String,Object>> contactos(){return db.queryForList("select id_tipo_contacto,codigo,descripcion,permite_otp from sira.cat_tipo_contacto where activo='S' order by id_tipo_contacto");}
}
