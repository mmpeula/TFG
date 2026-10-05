package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tarjeta del tablero Kanban. Es la unidad de trabajo asignable a un miembro del equipo. */
@Entity
@Table(name = "TASKS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"boardColumn", "assignees", "createdBy", "comments"})
@EqualsAndHashCode(of = "id")
public class Task extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tasks_seq")
    @SequenceGenerator(name = "tasks_seq", sequenceName = "SEQ_TASKS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Size(max = 200)
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** Posicion de la tarjeta dentro de su columna (soporte para drag & drop). */
    @Min(0)
    @Column(name = "POSITION_INDEX", nullable = false)
    @Builder.Default
    private Integer position = 0;

    /**
     * Tipo de tarea. La columna admite nulos a proposito: las tareas que ya
     * existian cuando se anadio el campo se leen como {@code TAREA}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "TASK_TYPE", length = 20)
    @Builder.Default
    private TaskType type = TaskType.TAREA;

    /**
     * Bloque de trabajo al que pertenece ("Autenticacion", "Memoria"...). Lo pone
     * la generacion de plan con IA; en las tareas creadas a mano queda vacio.
     */
    @Size(max = 100)
    @Column(name = "EPIC", length = 100)
    private String epic;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORITY", nullable = false, length = 20)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIA;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    /** Estimacion en horas; el modulo de IA la rellenara al generar la planificacion. */
    @Column(name = "ESTIMATED_HOURS", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    /**
     * Horas que de verdad ha costado, si quien la ha hecho las ha apuntado.
     * Opcional y libre: no hace falta que coincida con la estimacion. Sirve para
     * calibrar el ritmo real de cada persona en los proximos repartos con IA.
     */
    @Column(name = "ACTUAL_HOURS", precision = 6, scale = 2)
    private BigDecimal actualHours;

    /** true si la tarea fue creada automaticamente por el modulo de IA. */
    @Column(name = "AI_GENERATED", nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "COLUMN_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TASKS_COLUMN"))
    private BoardColumn boardColumn;

    /** Miembros del equipo responsables de la tarea (puede no tener ninguno). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "TASK_ASSIGNEES",
            joinColumns = @JoinColumn(name = "TASK_ID", foreignKey = @ForeignKey(name = "FK_TASK_ASSIGNEES_TASK")),
            inverseJoinColumns = @JoinColumn(name = "USER_ID",
                    foreignKey = @ForeignKey(name = "FK_TASK_ASSIGNEES_USER")),
            uniqueConstraints = @UniqueConstraint(name = "UQ_TASK_ASSIGNEES", columnNames = {"TASK_ID", "USER_ID"}))
    @OrderBy("fullName ASC")
    @Builder.Default
    private Set<User> assignees = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATED_BY_ID", foreignKey = @ForeignKey(name = "FK_TASKS_CREATOR"))
    private User createdBy;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
}
