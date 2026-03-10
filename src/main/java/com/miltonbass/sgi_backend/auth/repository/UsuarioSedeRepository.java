package com.miltonbass.sgi_backend.auth.repository;

import com.miltonbass.sgi_backend.auth.entity.UsuarioSede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsuarioSedeRepository extends JpaRepository<UsuarioSede, UUID> {

    /** Obtiene todas las sedes activas de un usuario */
    List<UsuarioSede> findByUsuarioIdAndActivoTrue(UUID usuarioId);

    /** Verifica si un usuario tiene acceso a una sede específica */
    boolean existsByUsuarioIdAndSedeIdAndActivoTrue(UUID usuarioId, UUID sedeId);
}