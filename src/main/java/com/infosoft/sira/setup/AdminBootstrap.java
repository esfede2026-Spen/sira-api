package com.infosoft.sira.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;
    private final boolean habilitado;
    private final String username;
    private final String correo;
    private final String password;

    public AdminBootstrap(
            JdbcTemplate db,
            PasswordEncoder passwordEncoder,
            @Value("${sira.admin-bootstrap-enabled:false}") boolean habilitado,
            @Value("${ADMIN_USERNAME:admin}") String username,
            @Value("${ADMIN_EMAIL:admin@infosoft.com.ve}") String correo,
            @Value("${ADMIN_PASSWORD:SiraAdmin2026*}") String password) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.habilitado = habilitado;
        this.username = username;
        this.correo = correo;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            return;
        }

        try {
            Integer cantidad = db.queryForObject(
                    "select count(*) from sira.seg_usuario where lower(username)=lower(?)",
                    Integer.class,
                    username
            );

            if (cantidad != null && cantidad > 0) {
                log.info("Administrador '{}' ya existe.", username);
                return;
            }

            long idPersona = obtenerOCrearPersonaAdministrador();

            db.update(
                    """
                    insert into sira.seg_usuario (
                        uuid_usuario,
                        id_persona,
                        username,
                        password_hash,
                        algoritmo_hash,
                        correo_acceso,
                        estado,
                        bloqueado,
                        requiere_cambio_password,
                        intentos_fallidos
                    )
                    values (?, ?, ?, ?, 'BCRYPT', lower(?), 'ACTIVO', 'N', 'N', 0)
                    """,
                    UUID.randomUUID(),
                    idPersona,
                    username,
                    passwordEncoder.encode(password),
                    correo
            );

            log.info("Administrador '{}' creado correctamente.", username);
        } catch (RuntimeException ex) {
            log.error("No se pudo crear el administrador.", ex);
        }
    }

    private long obtenerOCrearPersonaAdministrador() {
        List<Map<String, Object>> existentes = db.queryForList(
                "select id_persona from sira.per_persona where documento_numero='00000001' limit 1"
        );

        if (!existentes.isEmpty()) {
            return ((Number) existentes.get(0).get("id_persona")).longValue();
        }

        Long idPersona = db.queryForObject(
                """
                insert into sira.per_persona (
                    uuid_persona,
                    id_pais_emisor,
                    id_tipo_documento,
                    documento_numero,
                    primer_nombre,
                    primer_apellido,
                    nivel_confianza,
                    estado_registro,
                    registro_basico_completo,
                    registro_fase1_completo
                )
                values (?, 1, 1, '00000001', 'ADMINISTRADOR', 'SIRA', 0, 'ACTIVO', 'N', 'N')
                returning id_persona
                """,
                Long.class,
                UUID.randomUUID()
        );

        if (idPersona == null) {
            throw new IllegalStateException("PostgreSQL no devolvió id_persona para el administrador");
        }

        return idPersona;
    }
}
