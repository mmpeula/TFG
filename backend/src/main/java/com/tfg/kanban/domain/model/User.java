package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Usuario de la plataforma (alumno o profesor).
 *
 * La tabla se llama USERS porque USER es palabra reservada en Oracle.
 */
@Entity
@Table(name = "USERS", uniqueConstraints = @UniqueConstraint(name = "UK_USERS_EMAIL", columnNames = "EMAIL"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"passwordHash", "memberships"})
@EqualsAndHashCode(of = "id")
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq")
    @SequenceGenerator(name = "users_seq", sequenceName = "SEQ_USERS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Email
    @Column(name = "EMAIL", nullable = false, length = 180)
    private String email;

    /** Hash BCrypt de la contrasena. Nunca se expone en las respuestas de la API. */
    @NotBlank
    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @NotBlank
    @Size(max = 150)
    @Column(name = "FULL_NAME", nullable = false, length = 150)
    private String fullName;

    /**
     * Rol global. Se usa como rol por defecto al entrar en un equipo y para los
     * recursos que no dependen de un equipo. En los recursos de equipo/proyecto
     * manda el rol de {@link TeamMember}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MIEMBRO;

    @Column(name = "ENABLED", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Equipos a los que pertenece el usuario (lado inverso de la relacion). */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeamMember> memberships = new ArrayList<>();
}
