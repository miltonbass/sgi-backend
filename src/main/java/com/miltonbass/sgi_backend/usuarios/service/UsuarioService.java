package com.miltonbass.sgi_backend.usuarios.service;

import com.miltonbass.sgi_backend.auth.entity.UsuarioSistema;
import com.miltonbass.sgi_backend.auth.entity.UsuarioSede;
import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.repository.*;
import com.miltonbass.sgi_backend.exception.AuthException;
import com.miltonbass.sgi_backend.usuarios.dto.UsuarioDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    private static final String PLACEHOLDER_HASH =
        "$2a$10$PLACEHOLDER_HASH_HASTA_ACTIVACION_xxxxxxxxxxxxxxxxxxxxxxxxxx";

    private final UsuarioSistemaRepository usuarioRepo;
    private final UsuarioSedeRepository usuarioSedeRepo;
    private final SedeRepository sedeRepo;
    private final EmailService emailService;

    public UsuarioService(UsuarioSistemaRepository usuarioRepo,
                      UsuarioSedeRepository usuarioSedeRepo,
                      SedeRepository sedeRepo,
                      EmailService emailService) {
    this.usuarioRepo     = usuarioRepo;
    this.usuarioSedeRepo = usuarioSedeRepo;
    this.sedeRepo        = sedeRepo;
    this.emailService    = emailService;
    }

    // ─── Crear usuario ───────────────────────────────────────

    @Transactional
    public UsuarioResponse crearUsuario(CreateUsuarioRequest req,
                                         String rolLlamante,
                                         String sedeIdLlamante) {
        // 1. Email unico
        if (usuarioRepo.existsByEmail(req.email())) {
            throw AuthException.emailDuplicado();
        }

        // 2. Generar username si no viene
        String username = req.username() != null ? req.username()
            : req.email().split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 4);

        // 3. Token de activacion
        String tokenActivacion = UUID.randomUUID().toString();
        Instant expira = Instant.now().plusSeconds(86400); // 24h

        // 4. Construir entidad
        UsuarioSistema u = new UsuarioSistema();
        u.setEmail(req.email());
        u.setUsername(username);
        u.setNombre(req.nombre());
        u.setApellido(req.apellido());
        u.setTelefono(req.telefono());
        u.setPasswordHash(PLACEHOLDER_HASH);
        u.setActivo(false);    // activo=false hasta que active su cuenta
        u.setDebeCambiarPassword(true);
        u.setTokenResetPassword(tokenActivacion);
        u.setTokenResetExpira(expira);
        usuarioRepo.save(u);

        // 5. Asignacion inicial si viene en el request
        if (req.sedeId() != null && req.roles() != null && !req.roles().isEmpty()) {
            validarAsignacion(req.sedeId(), req.roles(), rolLlamante, sedeIdLlamante);
            asignarSede(u.getId(), req.sedeId(), req.roles());
        }

        // 6. Email de bienvenida (mock en dev)
        emailService.enviarActivacion(u.getEmail(), u.getNombre(), tokenActivacion);
        log.info("Usuario creado: {} | token activacion generado", u.getEmail());

        return toResponse(u);
    }

    // ─── Asignar sede ────────────────────────────────────────

    @Transactional
    public UsuarioResponse asignarSede(UUID usuarioId,
                                       AsignarSedeRequest req,
                                       String rolLlamante,
                                       String sedeIdLlamante) {
        UsuarioSistema u = usuarioRepo.findById(usuarioId)
            .orElseThrow(() -> AuthException.usuarioNoEncontrado());

        validarAsignacion(req.sedeId(), req.roles(), rolLlamante, sedeIdLlamante);
        asignarSede(usuarioId, req.sedeId(), req.roles());
        return toResponse(u);
    }

    // ─── Listar usuarios ─────────────────────────────────────

    public UsuarioListResponse listar(UUID sedeIdFiltro,
                                      String rolFiltro,
                                      int pagina,
                                      int tamano,
                                      String rolLlamante,
                                      String sedeIdLlamante) {
        // ADMIN_SEDE solo puede ver su propia sede
        if ("ADMIN_SEDE".equals(rolLlamante) && sedeIdFiltro == null) {
            sedeIdFiltro = UUID.fromString(sedeIdLlamante);
        }

        List<UsuarioSistema> usuarios = (sedeIdFiltro != null)
            ? usuarioRepo.findBySede(sedeIdFiltro, pagina * tamano, tamano)
            : usuarioRepo.findAll(PageRequest.of(pagina, tamano)).getContent();

        // Filtro adicional por rol si se especifico
        if (rolFiltro != null) {
            UUID filtroFinal = sedeIdFiltro;
            usuarios = usuarios.stream()
                .filter(u -> tieneRolEnSede(u.getId(), rolFiltro, filtroFinal))
                .toList();
        }

        return new UsuarioListResponse(
            usuarios.stream().map(this::toResponse).toList(),
            usuarios.size(), pagina, tamano
        );
    }

    // ─── Helpers internos ────────────────────────────────────

    private void validarAsignacion(UUID sedeId, List<String> roles,
                                   String rolLlamante, String sedeIdLlamante) {
        boolean esAdminGlobal = "ADMIN_GLOBAL".equals(rolLlamante)
                             || "SUPER_ADMIN".equals(rolLlamante);
        if (!esAdminGlobal) {
            // ADMIN_SEDE solo puede operar sobre su propia sede
            if (!sedeId.toString().equals(sedeIdLlamante)) {
                throw AuthException.sedeNoAsignada();
            }
            // ADMIN_SEDE no puede asignar ADMIN_GLOBAL
            if (roles.contains("ADMIN_GLOBAL") || roles.contains("SUPER_ADMIN")) {
                throw AuthException.rolNoPermitido();
            }
        }
        sedeRepo.findByIdAndActivaTrue(sedeId)
            .orElseThrow(() -> AuthException.sedeNoEncontrada());
    }

    private void asignarSede(UUID usuarioId, UUID sedeId, List<String> roles) {
        // Upsert: si ya existe la relacion, actualiza roles
        Optional<UsuarioSede> existente =
            usuarioSedeRepo.findByUsuarioIdAndSedeId(usuarioId, sedeId);
        UsuarioSede us = existente.orElseGet(UsuarioSede::new);
        us.setUsuarioId(usuarioId);
        us.setSedeId(sedeId);
        us.setRoles(roles);
        us.setActivo(true);
        usuarioSedeRepo.save(us);
    }

    private boolean tieneRolEnSede(UUID usuarioId, String rol, UUID sedeId) {
        return usuarioSedeRepo.findByUsuarioId(usuarioId).stream()
            .anyMatch(us -> (sedeId == null || us.getSedeId().equals(sedeId))
                        && us.getRoles().contains(rol));
    }

    private UsuarioResponse toResponse(UsuarioSistema u) {
        List<UsuarioSede> sedes = usuarioSedeRepo.findByUsuarioId(u.getId());
        List<SedeAsignada> sedesDto = sedes.stream().map(us -> {
            Sede s = sedeRepo.findById(us.getSedeId()).orElse(null);
            String cod = s != null ? s.getCodigo() : "?";
            String nom = s != null ? s.getNombreCorto() : "?";
            return new SedeAsignada(us.getSedeId().toString(), cod, nom, us.getRoles());
        }).toList();

        return new UsuarioResponse(
            u.getId().toString(), u.getEmail(), u.getUsername(),
            u.getNombre(), u.getApellido(), u.getTelefono(),
            u.isActivo(), u.isDebeCambiarPassword(),
            u.getUltimoLogin() != null ? u.getUltimoLogin().toString() : null,
            sedesDto
        );
    }

    @Transactional
    public UsuarioResponse actualizar(UUID id, UpdateUsuarioRequest req,
                                    String rolLlamante, String sedeIdLlamante) {
        UsuarioSistema u = usuarioRepo.findById(id)
            .orElseThrow(() -> AuthException.usuarioNoEncontrado());

        if (req.nombre()    != null) u.setNombre(req.nombre());
        if (req.apellido()  != null) u.setApellido(req.apellido());
        if (req.telefono()  != null) u.setTelefono(req.telefono());
        if (req.activo()    != null) u.setActivo(req.activo());

        usuarioRepo.save(u);
        return toResponse(u);
    }

    @Transactional
    public void desasignarSede(UUID usuarioId, UUID sedeId,
                            String rolLlamante, String sedeIdLlamante) {
        validarAsignacion(sedeId, List.of(), rolLlamante, sedeIdLlamante);
        usuarioSedeRepo.deleteByUsuarioIdAndSedeId(usuarioId, sedeId);
        log.info("Usuario {} desvinculado de sede {}", usuarioId, sedeId);
    }
}
