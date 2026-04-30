package com.miltonbass.sgi_backend.auth.service;

import com.miltonbass.sgi_backend.auth.dto.AuthDtos.*;
import com.miltonbass.sgi_backend.auth.entity.RefreshToken;
import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.entity.UsuarioSede;
import com.miltonbass.sgi_backend.auth.entity.UsuarioSistema;
import com.miltonbass.sgi_backend.auth.repository.SedeRepository;
import com.miltonbass.sgi_backend.auth.repository.UsuarioSedeRepository;
import com.miltonbass.sgi_backend.auth.repository.UsuarioSistemaRepository;
import com.miltonbass.sgi_backend.config.JwtProperties;
import com.miltonbass.sgi_backend.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica central de autenticación.
 *
 * Flujos:
 * login() → valida credenciales, selecciona sede, emite JWT + refresh token
 * refresh() → rota el refresh token, emite nuevo JWT
 * logout() → revoca el refresh token
 */
@Service
public class AuthService {

        private static final Logger log = LoggerFactory.getLogger(AuthService.class);

        private final UsuarioSistemaRepository usuarioRepo;
        private final UsuarioSedeRepository usuarioSedeRepo;
        private final SedeRepository sedeRepo;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;
        private final ConfiguracionSistemaService config;
        private final JwtProperties jwtProperties;
        private final BCryptPasswordEncoder passwordEncoder;

        public AuthService(UsuarioSistemaRepository usuarioRepo,
                        UsuarioSedeRepository usuarioSedeRepo,
                        SedeRepository sedeRepo,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService,
                        ConfiguracionSistemaService config,
                        JwtProperties jwtProperties) {
                this.usuarioRepo = usuarioRepo;
                this.usuarioSedeRepo = usuarioSedeRepo;
                this.sedeRepo = sedeRepo;
                this.jwtService = jwtService;
                this.refreshTokenService = refreshTokenService;
                this.config = config;
                this.jwtProperties = jwtProperties;
                this.passwordEncoder = new BCryptPasswordEncoder();
        }

        // ─── Login ────────────────────────────────────────────────────────────────

        /**
         * Autentica al usuario y emite los tokens.
         *
         * @param request     credenciales + sede opcional
         * @param dispositivo IP + user-agent para auditoría
         * @return TokenResponse con access y refresh token
         */
        @Transactional
        public TokenResponse login(LoginRequest request, String dispositivo) {
                // 1. Buscar usuario
                UsuarioSistema usuario = usuarioRepo.findByEmailIgnoreCase(request.email())
                                .orElseThrow(() -> AuthException.credencialesInvalidas());

                // 2. Verificar cuenta activa
                if (!usuario.isActivo()) {
                        throw AuthException.cuentaInactiva();
                }

                // 3. Verificar bloqueo por intentos fallidos
                if (usuario.estaBloqueado()) {
                        log.warn("Login bloqueado para usuario {}", usuario.getEmail());
                        throw AuthException.cuentaBloqueada(usuario.getBloqueadoHasta());
                }

                // 4. Verificar contraseña
                if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
                        // Registrar intento fallido con bloqueo automático si aplica
                        Instant bloqueadoHasta = Instant.now().plusSeconds(config.getBloqueoMinutos() * 60);
                        usuarioRepo.incrementarIntentosFallidos(
                                        usuario.getId(),
                                        config.getMaxIntentos(),
                                        bloqueadoHasta);
                        log.warn("Contraseña incorrecta para {}, intentos: {}",
                                        usuario.getEmail(), usuario.getIntentosFallidos() + 1);
                        throw AuthException.credencialesInvalidas();
                }

                // 5. Resetear intentos fallidos
                usuarioRepo.resetearIntentos(usuario.getId(), Instant.now());

                // 6. Obtener sedes del usuario
                List<UsuarioSede> usuarioSedes = usuarioSedeRepo.findByUsuarioIdAndActivoTrue(usuario.getId());
                if (usuarioSedes.isEmpty()) {
                        throw AuthException.sinSedesAsignadas();
                }

                // 7. Seleccionar la sede activa
                UsuarioSede sedeSeleccionada = seleccionarSede(usuarioSedes, request.sedeId());
                Sede sede = sedeRepo.findByIdAndActivaTrue(sedeSeleccionada.getSedeId())
                                .orElseThrow(() -> AuthException.sedeNoDisponible());

                // 8. Construir lista de SedeInfo para la respuesta
                List<SedeInfo> sedesInfo = construirSedesInfo(usuarioSedes);

                // 9. Generar tokens
                String accessToken = jwtService.generarAccessToken(
                                usuario.getEmail(),
                                usuario.getId().toString(),
                                sede.getId().toString(),
                                sede.getSchemaName(),
                                sedeSeleccionada.getRoles());
                String refreshTokenValue = refreshTokenService.crearRefreshToken(usuario.getId(), dispositivo);

                log.info("Login exitoso: {} → sede {}", usuario.getEmail(), sede.getCodigo());

                return construirTokenResponse(
                                accessToken,
                                refreshTokenValue,
                                usuario,
                                sede,
                                sedeSeleccionada.getRoles(),
                                sedesInfo);
        }

        // ─── Refresh ──────────────────────────────────────────────────────────────

        /**
         * Rota el refresh token y emite un nuevo access token.
         */
        @Transactional
        public TokenResponse refresh(RefreshRequest request, String dispositivo) {
                // 1. Rotar el refresh token (valida + revoca + crea nuevo)
                RefreshToken tokenAntiguo = refreshTokenService.validar(request.refreshToken())
                                .orElseThrow(() -> AuthException.refreshTokenInvalido());

                String nuevoRefreshToken = refreshTokenService.rotarToken(request.refreshToken(), dispositivo)
                                .orElseThrow(() -> AuthException.refreshTokenInvalido());

                // 2. Cargar usuario
                UUID usuarioId = java.util.Objects.requireNonNull(
                                tokenAntiguo.getUsuarioId(), "usuarioId no puede ser nulo en el refresh token");
                UsuarioSistema usuario = usuarioRepo.findById(usuarioId)
                                .orElseThrow(() -> AuthException.credencialesInvalidas());

                // 3. Obtener la primera sede activa (para refresh se mantiene la misma lógica)
                List<UsuarioSede> usuarioSedes = usuarioSedeRepo.findByUsuarioIdAndActivoTrue(usuarioId);
                if (usuarioSedes.isEmpty()) {
                        throw AuthException.sinSedesAsignadas();
                }
                UsuarioSede sedeSeleccionada = usuarioSedes.get(0);
                Sede sede = sedeRepo.findByIdAndActivaTrue(sedeSeleccionada.getSedeId())
                                .orElseThrow(() -> AuthException.sedeNoDisponible());

                // 4. Generar nuevo access token
                String nuevoAccessToken = jwtService.generarAccessToken(
                                usuario.getEmail(),
                                usuario.getId().toString(),
                                sede.getId().toString(),
                                sede.getSchemaName(),
                                sedeSeleccionada.getRoles());

                List<SedeInfo> sedesInfo = construirSedesInfo(usuarioSedes);

                log.debug("Refresh exitoso para usuario {}", usuario.getEmail());

                return construirTokenResponse(nuevoAccessToken, nuevoRefreshToken, usuario, sede,
                                sedeSeleccionada.getRoles(), sedesInfo);
        }

        // ─── Sedes por email (pre-login) ─────────────────────────────────────────

        /**
         * Retorna las sedes activas de un usuario dado su email.
         * Usado por el frontend para mostrar el selector de sede antes del login.
         * Devuelve lista vacía si el email no existe (sin revelar si existe o no).
         */
        public List<SedeInfo> obtenerSedesPorEmail(String email) {
                return usuarioRepo.findByEmailIgnoreCase(email)
                                .filter(UsuarioSistema::isActivo)
                                .map(u -> {
                                        List<UsuarioSede> sedes = usuarioSedeRepo
                                                        .findByUsuarioIdAndActivoTrue(u.getId());
                                        return construirSedesInfo(sedes);
                                })
                                .orElse(List.of());
        }

        // ─── Logout ───────────────────────────────────────────────────────────────

        /**
         * Invalida el refresh token del dispositivo actual.
         */
        @Transactional
        public void logout(LogoutRequest request) {
                refreshTokenService.revocarUno(request.refreshToken());
                log.debug("Logout: refresh token revocado");
        }

        // ─── Helpers ─────────────────────────────────────────────────────────────

        /**
         * Selecciona la sede a usar en el login.
         * Si sedeId viene en el request, valida que el usuario tenga acceso.
         * Si no, y el usuario tiene solo una sede, la usa automáticamente.
         * Si tiene varias y no se especificó, lanza error.
         */
        private UsuarioSede seleccionarSede(List<UsuarioSede> sedes, String sedeIdStr) {
                if (sedeIdStr != null && !sedeIdStr.isBlank()) {
                        UUID sedeId = UUID.fromString(sedeIdStr);
                        return sedes.stream()
                                        .filter(us -> us.getSedeId().equals(sedeId))
                                        .findFirst()
                                        .orElseThrow(() -> AuthException.sedeNoAsignada());
                }

                if (sedes.size() == 1) {
                        return sedes.get(0);
                }

                // Múltiples sedes: el cliente debe especificar cuál
                throw AuthException.multisedeSinSeleccion();
        }

        private List<SedeInfo> construirSedesInfo(List<UsuarioSede> usuarioSedes) {
                return usuarioSedes.stream()
                                .map(us -> sedeRepo.findByIdAndActivaTrue(us.getSedeId())
                                                .map(s -> new SedeInfo(s.getId().toString(), s.getCodigo(),
                                                                s.getNombreCorto()))
                                                .orElse(null))
                                .filter(s -> s != null)
                                .toList();
        }

        private TokenResponse construirTokenResponse(String accessToken,
                        String refreshToken,
                        UsuarioSistema usuario,
                        Sede sede,
                        List<String> roles,
                        List<SedeInfo> sedes) {
                long expiresIn = jwtProperties.getExpirationMinutes() * 60;

                UsuarioInfo usuarioInfo = new UsuarioInfo(
                                usuario.getId().toString(),
                                usuario.getEmail(),
                                sede.getCodigo(),
                                sede.getSchemaName(),
                                roles,
                                sedes);

                return new TokenResponse(accessToken, refreshToken, expiresIn, "Bearer", usuarioInfo);
        }
}