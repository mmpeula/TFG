package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Pertenencia de un usuario a un equipo, con el rol que tiene <b>en ese equipo</b>.
 *
 * Sustituye a la relacion N:M simple de la fase de scaffolding: el rol global de
 * {@link User} solo se usa como valor por defecto y para los recursos que no
 * cuelgan de un equipo; para todo lo demas manda el rol de esta entidad.
 */
@Entity
@Table(name = "TEAM_MEMBERS", uniqueConstraints = @UniqueConstraint(
        name = "UK_TEAM_MEMBERS", columnNames = {"TEAM_ID", "USER_ID"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"team", "user"})
@EqualsAndHashCode(of = "id")
public class TeamMember extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_members_seq")
    @SequenceGenerator(name = "team_members_seq", sequenceName = "SEQ_TEAM_MEMBERS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TEAM_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TM_TEAM"))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TM_USER"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MIEMBRO;
}
