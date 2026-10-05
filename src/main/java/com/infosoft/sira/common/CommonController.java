package com.infosoft.sira.common;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1")
public class CommonController {private final JdbcTemplate db;public CommonController(JdbcTemplate db){this.db=db;}@GetMapping("/salud") public Map<String,Object> salud(){Integer ok=db.queryForObject("select 1",Integer.class);return Map.of("estado","OK","aplicacion","SIRA API V3","baseDatos",ok!=null?"OK":"ERROR","propietario","Infosoft, C.A. J-30984159-9");}}
