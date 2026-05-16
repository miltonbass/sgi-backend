package com.miltonbass.sgi_backend.configuracion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
public class ConfiguracionSmtpService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSmtpService.class);
    private static final String DOMINIO = "smtp";
    private static final String SALT    = "5f7a8b9c0d1e2f3a";

    private final JdbcTemplate  jdbc;
    private final ObjectMapper  mapper;
    private final String        encryptionKey;

    public ConfiguracionSmtpService(JdbcTemplate jdbc,
                                    ObjectMapper mapper,
                                    @Value("${sgi.encryption.key}") String encryptionKey) {
        this.jdbc          = jdbc;
        this.mapper        = mapper;
        this.encryptionKey = encryptionKey;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    public ConfiguracionSmtpResponse obtener() {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) {
            return new ConfiguracionSmtpResponse(null, null, null, null, null, null, false, false);
        }
        return toResponse(cfg);
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    public ConfiguracionSmtpResponse actualizar(ActualizarSmtpRequest req, UUID usuarioId) {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) cfg = new HashMap<>();

        cfg.put("host",      req.host());
        cfg.put("puerto",    req.puerto());
        cfg.put("usuario",   req.usuario());
        cfg.put("cifrado",   req.cifrado() != null ? req.cifrado() : "STARTTLS");
        cfg.put("remitente", req.remitente());
        cfg.put("activo",    req.activo());

        if (req.password() != null && !req.password().isBlank()) {
            cfg.put("passwordEncriptado", encriptar(req.password()));
        }

        guardar(cfg, usuarioId);
        log.info("[SMTP] Configuración actualizada por {}", usuarioId);
        return toResponse(cfg);
    }

    // ── POST /probar ──────────────────────────────────────────────────────────

    public ProbarSmtpResponse probar(String emailDestino) {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) {
            return new ProbarSmtpResponse(false, "No hay configuración SMTP guardada.");
        }
        String encriptado = (String) cfg.get("passwordEncriptado");
        if (encriptado == null) {
            return new ProbarSmtpResponse(false, "No hay contraseña SMTP configurada.");
        }
        try {
            JavaMailSenderImpl sender = buildSender(cfg, descifrar(encriptado));
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom((String) cfg.get("remitente"));
            h.setTo(emailDestino);
            h.setSubject("SGI — Correo de prueba");
            h.setText("Este es un correo de prueba del sistema SGI.\n\n"
                    + "Si lo recibiste, el SMTP está configurado correctamente.");
            sender.send(msg);
            log.info("[SMTP] Prueba exitosa → {}", emailDestino);
            return new ProbarSmtpResponse(true, "Email de prueba enviado a " + emailDestino);
        } catch (Exception e) {
            log.warn("[SMTP] Prueba fallida: {}", e.getMessage());
            return new ProbarSmtpResponse(false, e.getMessage());
        }
    }

    // ── Usado por ProdEmailService ────────────────────────────────────────────

    public record SmtpActivo(
            String host, int puerto, String usuario,
            String password, String cifrado, String remitente) {}

    public Optional<SmtpActivo> obtenerSmtpActivo() {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.get("activo"))) return Optional.empty();
        String encriptado = (String) cfg.get("passwordEncriptado");
        if (encriptado == null) return Optional.empty();
        try {
            return Optional.of(new SmtpActivo(
                    (String) cfg.get("host"),
                    ((Number) cfg.get("puerto")).intValue(),
                    (String) cfg.get("usuario"),
                    descifrar(encriptado),
                    (String) cfg.getOrDefault("cifrado", "STARTTLS"),
                    (String) cfg.get("remitente")));
        } catch (Exception e) {
            log.error("[SMTP] Error al descifrar config activa: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Map<String, Object> leerConfig() {
        List<String> rows = jdbc.queryForList(
                "SELECT config::text FROM shared.configuracion_global WHERE dominio = ?",
                String.class, DOMINIO);
        if (rows.isEmpty() || rows.get(0) == null) return null;
        try {
            return mapper.readValue(rows.get(0), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[SMTP] Error parseando config JSONB: {}", e.getMessage());
            return null;
        }
    }

    private void guardar(Map<String, Object> cfg, UUID usuarioId) {
        try {
            String json = mapper.writeValueAsString(cfg);
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO shared.configuracion_global (dominio, config, updated_at, updated_by)
                        VALUES (?, CAST(? AS jsonb), now(), ?)
                        ON CONFLICT (dominio) DO UPDATE
                        SET config     = EXCLUDED.config,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by
                        """);
                ps.setString(1, DOMINIO);
                ps.setObject(2, json, java.sql.Types.OTHER);
                ps.setObject(3, usuarioId);
                return ps;
            });
        } catch (Exception e) {
            throw new RuntimeException("Error guardando config SMTP: " + e.getMessage(), e);
        }
    }

    private ConfiguracionSmtpResponse toResponse(Map<String, Object> cfg) {
        Number puerto = (Number) cfg.get("puerto");
        return new ConfiguracionSmtpResponse(
                (String)  cfg.get("host"),
                puerto != null ? puerto.intValue() : null,
                (String)  cfg.get("usuario"),
                cfg.containsKey("passwordEncriptado") ? "••••••••" : null,
                (String)  cfg.get("cifrado"),
                (String)  cfg.get("remitente"),
                Boolean.TRUE.equals(cfg.get("activo")),
                true);
    }

    private JavaMailSenderImpl buildSender(Map<String, Object> cfg, String password) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost((String) cfg.get("host"));
        s.setPort(((Number) cfg.get("puerto")).intValue());
        s.setUsername((String) cfg.get("usuario"));
        s.setPassword(password);

        Properties p = s.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth",              "true");
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout",           "5000");

        switch ((String) cfg.getOrDefault("cifrado", "NONE")) {
            case "TLS" -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.ssl.trust",  cfg.get("host"));
            }
            case "STARTTLS" -> {
                p.put("mail.smtp.starttls.enable",   "true");
                p.put("mail.smtp.starttls.required", "true");
            }
            default -> {}
        }
        return s;
    }

    private String encriptar(String texto) {
        return Encryptors.text(encryptionKey, SALT).encrypt(texto);
    }

    private String descifrar(String encriptado) {
        return Encryptors.text(encryptionKey, SALT).decrypt(encriptado);
    }
}
