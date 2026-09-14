package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Equipo de trabajo: conjunto de usuarios que colaboran en uno o varios proyectos. */
@Entity
@Table(name = "TEAMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"members", "projects"})
@EqualsAndHashCode(of = "id")
public class Team extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "teams_seq")
    @SequenceGenerator(name = "teams_seq", sequenceName = "SEQ_TEAMS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(name = "NAME", nullable = false, length = 120)
    private String name;

    @Size(max = 500)
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** Usuario que creo el equipo; normalmente tiene rol RESPONSABLE. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OWNER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TEAMS_OWNER"))
    private User owner;

    /** Miembros del equipo, cada uno con su rol dentro de este equipo. */
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeamMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Project> projects = new ArrayList<>();
}
