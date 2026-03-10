package com.miltonbass.sgi_backend.auth.repository;

import com.miltonbass.sgi_backend.auth.entity.UsuarioSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, UUID> {

    Optional<UsuarioSistema> findByEmailIgnoreCase(String email);

    /** Resetea intentos fallidos y registra ultimo_login tras login exitoso */
    @Modifying
    @Query("""
            UPDATE UsuarioSistema u
            SET u.intentosFallidos = 0,
                u.bloqueadoHasta = null,
                u.ultimoLogin = :ahora
            WHERE u.id = :id
            """)
    void resetearIntentos(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /** Incrementa intentos fallidos y bloquea si supera el máximo */
    @Modifying
    @Query("""
            UPDATE UsuarioSistema u
            SET u.intentosFallidos = u.intentosFallidos + 1,
                u.bloqueadoHasta = CASE
                    WHEN u.intentosFallidos + 1 >= :maxIntentos THEN :bloqueadoHasta
                    ELSE u.bloqueadoHasta
                END
            WHERE u.id = :id
            """)
    void incrementarIntentosFallidos(@Param("id") UUID id,
                                     @Param("maxIntentos") int maxIntentos,
                                     @Param("bloqueadoHasta") Instant bloqueadoHasta);
}