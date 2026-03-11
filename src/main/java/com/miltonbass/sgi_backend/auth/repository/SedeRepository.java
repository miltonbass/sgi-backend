package com.miltonbass.sgi_backend.auth.repository;

import com.miltonbass.sgi_backend.auth.entity.Sede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SedeRepository extends JpaRepository<Sede, UUID> {

    Optional<Sede> findByIdAndActivaTrue(UUID id);

    Optional<Sede> findByCodigoAndActivaTrue(String codigo);

    boolean existsByCodigo(String codigo);
}