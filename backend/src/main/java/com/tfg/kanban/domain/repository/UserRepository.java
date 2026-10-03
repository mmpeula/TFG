package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Busqueda por nombre o email; se usa al invitar gente a un equipo. */
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);

    /**
     * Para el arranque: si ya hay un administrador que pueda entrar no hace falta
     * crear otro. Cuentan solo las habilitadas: un ADMIN dado de baja no administra nada.
     */
    boolean existsByRoleAndEnabledTrue(Role role);

    /** Para no dejar la plataforma sin nadie que la administre: solo cuentan las cuentas que pueden entrar. */
    long countByRoleAndEnabledTrue(Role role);
}
