package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Proyecto = practica o trabajo de una asignatura.
 *
 * El campo {@code briefing} guarda el guion de la practica: es la entrada
 * que consumira el modulo de IA para generar la planificacion automatica.
 */
@Entity
@Table(name = "PROJECTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"boards", "activityLogs"})
@EqualsAndHashCode(of = "id")
public class Project extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "projects_seq")
    @SequenceGenerator(name = "projects_seq", sequenceName = "SEQ_PROJECTS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Size(max = 1000)
    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    /** Guion de la practica en texto plano (entrada del modulo de IA). */
    @Lob
    @Column(name = "BRIEFING")
    private String briefing;

    /**
     * Cuando empieza el trabajo. Junto con {@code deadline} define la ventana que
     * se reparte en sprints; si esta vacia se usa la fecha de la primera tarea.
     */
    @Column(name = "START_DATE")
    private LocalDate startDate;

    /** Fecha de entrega de la practica. */
    @Column(name = "DEADLINE")
    private LocalDate deadline;

    /**
     * En cuantos dias dura cada sprint del plan. Se fija al crear el workspace y
     * vale para todos sus sprints; nulo solo en los workspaces creados antes de
     * que existiera este campo, que se leen como 14 dias (dos semanas).
     */
    @Column(name = "SPRINT_DAYS")
    private Integer sprintDays;

    /**
     * Color e icono con los que se presenta el workspace en la pantalla inicial.
     * Son opcionales y puramente visuales: sirven para que el estudiante reconozca
     * su asignatura de un vistazo, no cambian ningun comportamiento.
     */
    @Size(max = 20)
    @Column(name = "COLOR", length = 20)
    private String color;

    @Size(max = 8)
    @Column(name = "ICON", length = 8)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PLANIFICACION;

    /**
     * Si el workspace esta operativo. Lo suspende y lo reactiva quien administra
     * la plataforma, para poder parar un workspace sin borrarlo.
     *
     * <p>Es un campo aparte de {@link #status} a proposito: ese lo lleva el propio
     * equipo (planificacion, en curso, entregado, archivado) y dice en que punto
     * esta su trabajo; este lo lleva soporte y dice si se puede trabajar. Si
     * fueran lo mismo, el equipo podria levantarse la suspension solo.</p>
     *
     * <p>Suspendido no es borrado: no se pierde nada y basta con reactivarlo para
     * que todo vuelva a estar donde estaba.</p>
     */
    @Column(name = "ENABLED", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TEAM_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_PROJECTS_TEAM"))
    private Team team;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Board> boards = new ArrayList<>();

    /** Historial de actividad; se borra en cascada al eliminar el proyecto. */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ActivityLog> activityLogs = new ArrayList<>();
}
