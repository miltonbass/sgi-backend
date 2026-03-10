package com.miltonbass.sgi_backend.auth.repository;

import com.miltonbass.sgi_backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Busca por hash SHA-256 del token */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca todos los refresh tokens de un usuario (logout total) */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.activo = false WHERE rt.usuarioId = :usuarioId AND rt.activo = true")
    int revocarTodosPorUsuario(@Param("usuarioId") UUID usuarioId);

    /** Limpieza de tokens expirados o revocados */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiraEn < :ahora OR rt.activo = false")
    int eliminarExpiradosYRevocados(@Param("ahora") Instant ahora);
}