package com.infosoft.sira.auth;
import com.infosoft.sira.security.JwtService;import jakarta.validation.Valid;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final JdbcTemplate db;private final PasswordEncoder encoder;private final JwtService jwt;
    public AuthController(JdbcTemplate db,PasswordEncoder encoder,JwtService jwt){this.db=db;this.encoder=encoder;this.jwt=jwt;}
    @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody LoginRequest r){
        List<Map<String,Object>> rows=db.queryForList("select id_usuario,username,password_hash,estado,id_persona from sira.seg_usuario where lower(username)=lower(?) or lower(coalesce(correo_acceso,''))=lower(?)",r.username(),r.username());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciales invalidas");
        Map<String,Object> u=rows.get(0);if(!"ACTIVO".equals(String.valueOf(u.get("estado")))||!encoder.matches(r.password(),String.valueOf(u.get("password_hash"))))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciales invalidas");
        return Map.of("token",jwt.crearToken(String.valueOf(u.get("username"))),"usuario",u.get("username"),"personaId",u.get("id_persona"));
    }
}
