// src/main/java/com/miltonbass/sgi_backend/miembros/repository/MiembroRepository.java
package com.miltonbass.sgi_backend.miembros.repository;

import com.miltonbass.sgi_backend.miembros.entity.Miembro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Todas las queries corren contra el schema del tenant activo —
 * SgiConnectionProvider ya hizo SET search_path antes de que llegue aquí.
 *
 * NO usar nativeQuery a menos que sea imprescindible — JPQL usa el
 * entity name "Miembro" que Hibernate resuelve al schema correcto.
 */
@Repository
public interface MiembroRepository extends JpaRepository<Miembro, UUID> {

  Page<Miembro> findAllByDeletedAtIsNull(Pageable pageable);

  Page<Miembro> findAllByEstadoAndDeletedAtIsNull(String estado, Pageable pageable);

  Optional<Miembro> findByIdAndDeletedAtIsNull(UUID id);

  /** Email único dentro del tenant (search_path ya está seteado) */
  @Query(value = "SELECT COUNT(m.id) FROM miembros m WHERE m.email = :email AND m.deleted_at IS NULL", nativeQuery = true)
  long countByEmail(@Param("email") String email);

  /** Email único excluyendo al propio miembro (para updates) */
  @Query(value = "SELECT COUNT(m.id) FROM miembros m WHERE m.email = :email AND m.id != CAST(:id AS uuid) AND m.deleted_at IS NULL", nativeQuery = true)
  long countByEmailExcluyendo(@Param("email") String email, @Param("id") String id);

  @Query(value = "SELECT COUNT(m.id) FROM miembros m WHERE m.cedula = :cedula AND m.deleted_at IS NULL", nativeQuery = true)
  long countByCedula(@Param("cedula") String cedula);

  /**
   * Búsqueda por nombre, apellido o email — con filtro de estado opcional.
   * :estado puede ser null para omitir el filtro.
   */
  @Query("""
      SELECT m FROM Miembro m
      WHERE m.deletedAt IS NULL
        AND (:estado IS NULL OR m.estado = :estado)
        AND (
             LOWER(m.nombres)   LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(m.apellidos) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(m.email)     LIKE LOWER(CONCAT('%', :q, '%'))
        )
      ORDER BY m.apellidos ASC, m.nombres ASC
      """)
  Page<Miembro> buscar(@Param("q") String q,
      @Param("estado") String estado,
      Pageable pageable);
}