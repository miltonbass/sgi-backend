package com.miltonbass.sgi_backend.auth.service;

import com.miltonbass.sgi_backend.auth.entity.RefreshToken;
import com.miltonbass.sgi_backend.auth.repository.RefreshTokenRepository;
import com.miltonbass.sgi_backend.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida de los refresh tokens.
 * La columna "activo" de la tabla equivale a "no revocado":
 *   activo = true  → token válido
 *   activo = false → token revocado
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    // ─── Creación ─────────────────────────────────────────────────────────────

    /**
     * Genera un refresh token aleatorio, persiste su hash y retorna el valor en claro.
     */
    @Transactional
    public String crearRefreshToken(UUID usuarioId, String dispositivo) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String tokenEnClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setUsuarioId(usuarioId);
        rt.setTokenHash(hashSha256(tokenEnClaro));
        rt.setExpiraEn(Instant.now().plus(jwtProperties.getRefreshExpirationDays(), ChronoUnit.DAYS));
        rt.setActivo(true);
        rt.setDispositivo(dispositivo);

        refreshTokenRepository.save(rt);
        log.debug("Refresh token creado para usuario {}", usuarioId);
        return tokenEnClaro;
    }

    // ─── Validación ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<RefreshToken> validar(String tokenEnClaro) {
        String hash = hashSha256(tokenEnClaro);
        return refreshTokenRepository.findByTokenHash(hash)
                .filter(RefreshToken::esValido);
    }

    // ─── Rotation ─────────────────────────────────────────────────────────────

    /**
     * Revoca el token actual y crea uno nuevo.
     */
    @Transactional
    public Optional<String> rotarToken(String tokenEnClaro, String dispositivo) {
        String hash = hashSha256(tokenEnClaro);
        Optional<RefreshToken> existente = refreshTokenRepository.findByTokenHash(hash);

        if (existente.isEmpty()) {
            log.warn("Intento de rotation con token no encontrado");
            return Optional.empty();
        }

        RefreshToken rt = existente.get();

        if (!rt.esValido()) {
            // Token ya revocado — posible robo, revocar todos por seguridad
            if (!rt.isActivo()) {
                log.warn("Token revocado reutilizado para usuario {}. Revocando todos.", rt.getUsuarioId());
                refreshTokenRepository.revocarTodosPorUsuario(rt.getUsuarioId());
            }
            return Optional.empty();
        }

        // Revocar el actual
        rt.setActivo(false);
        refreshTokenRepository.save(rt);

        // Emitir uno nuevo
        return Optional.of(crearRefreshToken(rt.getUsuarioId(), dispositivo));
    }

    // ─── Revocación ───────────────────────────────────────────────────────────

    @Transactional
    public void revocarTodos(UUID usuarioId) {
        int n = refreshTokenRepository.revocarTodosPorUsuario(usuarioId);
        log.debug("Revocados {} refresh tokens para usuario {}", n, usuarioId);
    }

    @Transactional
    public void revocarUno(String tokenEnClaro) {
        String hash = hashSha256(tokenEnClaro);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setActivo(false);
            refreshTokenRepository.save(rt);
        });
    }

    // ─── Limpieza automática ──────────────────────────────────────────────────

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void limpiarTokensExpirados() {
        int n = refreshTokenRepository.eliminarExpiradosYRevocados(Instant.now());
        log.info("Limpieza de refresh tokens: {} eliminados", n);
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private static String hashSha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}