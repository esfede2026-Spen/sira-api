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
    public void run(ApplicationArguments args) {
        try {
            db.execute("create table if not exists sira.per_persona_otro_grupo (id_persona bigint primary key, otro_grupo varchar(250), fecha_registro timestamp default current_timestamp)");
        } catch (Exception ex) {
            log.warn("Aviso en bootstrap de per_persona_otro_grupo: {}", ex.getMessage());
        }

        try {
            db.execute("""
                do $$
                declare
                    r record;
                    max_id bigint;
                begin
                    for r in
                        select s.relname as seq, t.relname as tbl, a.attname as col
                        from pg_class s
                        join pg_depend d on d.objid = s.oid and d.classid = 'pg_class'::regclass and d.refclassid = 'pg_class'::regclass
                        join pg_class t on t.oid = d.refobjid
                        join pg_attribute a on a.attrelid = t.oid and a.attnum = d.refobjsubid
                        join pg_namespace n on n.oid = s.relnamespace
                        where n.nspname = 'sira' and s.relkind = 'S'
                    loop
                        execute format('select coalesce(max(%I), 0) from sira.%I', r.col, r.tbl) into max_id;
                        if max_id > 0 then
                            execute format('select setval(''sira.%I'', %s, true)', r.seq, max_id);
                        end if;
                    end loop;
                end $$;
            """);
            log.info("Secuencias de PostgreSQL en esquema sira sincronizadas exitosamente.");
        } catch (Exception ex) {
            log.warn("Aviso al sincronizar secuencias en bootstrap: {}", ex.getMessage());
        }

        try {
            // Asegurar creación del rol ROL_INVITADOR
            Integer tieneRol = db.queryForObject("select count(*) from sira.seg_rol where upper(codigo) in ('ROL_INVITADOR', 'INVITADOR')", Integer.class);
            Long rolInvitadorId;
            if (tieneRol == null || tieneRol == 0) {
                rolInvitadorId = db.queryForObject(
                    "insert into sira.seg_rol (codigo, nombre, descripcion, es_sistema, activo) values ('ROL_INVITADOR', 'Invitador', 'Permite generar enlaces de invitación para registro público.', 'S', 'S') returning id_rol",
                    Long.class
                );
                log.info("Rol 'ROL_INVITADOR' creado con id {}", rolInvitadorId);
            } else {
                rolInvitadorId = db.queryForObject("select id_rol from sira.seg_rol where upper(codigo) in ('ROL_INVITADOR', 'INVITADOR') limit 1", Long.class);
            }

            // Asignar rol ROL_INVITADOR a los usuarios: admin, cesar.almeida, diluvina.perez
            List<String> invitadoresUsers = List.of("admin", "cesar.almeida", "diluvina.perez");
            for (String u : invitadoresUsers) {
                List<Long> uids = db.queryForList("select id_usuario from sira.seg_usuario where lower(username) = lower(?)", Long.class, u);
                for (Long uid : uids) {
                    db.update("""
                        insert into sira.seg_usuario_rol (id_usuario, id_rol, activo, fecha_desde)
                        values (?, ?, 'S', clock_timestamp())
                        on conflict (id_usuario, id_rol) do update set activo = 'S', fecha_hasta = null
                    """, uid, rolInvitadorId);
                    log.info("Rol INVITADOR asegurado al usuario '{}' (id: {})", u, uid);
                }
            }
        } catch (Exception ex) {
            log.error("Error asegurando roles de invitacion: {}", ex.getMessage(), ex);
        }

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
