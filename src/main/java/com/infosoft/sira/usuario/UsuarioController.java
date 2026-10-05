package com.infosoft.sira.usuario;

import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(JdbcTemplate db, PasswordEncoder passwordEncoder) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return db.queryForList(
                """
                select u.id_usuario, u.uuid_usuario, u.username, u.correo_acceso,
                       u.estado, u.bloqueado, u.id_persona,
                       p.documento_numero, p.primer_nombre, p.primer_apellido
                from sira.seg_usuario u
                join sira.per_persona p on p.id_persona=u.id_persona
                order by u.id_usuario desc
                """
        );
    }

    @PostMapping
    @Transactional
    public Map<String, Object> crear(@Valid @RequestBody UsuarioRequest request) {
        db.update(
                """
                insert into sira.seg_usuario (
                    uuid_usuario, id_persona, username, password_hash,
                    algoritmo_hash, correo_acceso, estado, bloqueado,
                    requiere_cambio_password, intentos_fallidos
                )
                values (?, ?, ?, ?, 'BCRYPT', lower(?), 'ACTIVO', 'N', 'S', 0)
                """,
                UUID.randomUUID(),
                request.personaId(),
                request.username(),
                passwordEncoder.encode(request.password()),
                request.correoAcceso()
        );

        return Map.of("creado", true);
    }
}
