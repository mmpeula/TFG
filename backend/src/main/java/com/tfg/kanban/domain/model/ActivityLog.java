package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

/**
 * Historial de actividad: traza de las acciones realizadas sobre el proyecto.
 *
 * Se guarda de forma generica ({@code entityType} + {@code entityId}) para no
 * tener que anadir una FK por cada tipo de entidad auditable.
 */
@Entity
@Table(name = "ACTIVITY_LOGS", indexes = {
        @Index(name = "IX_ACTIVITY_PROJECT", columnList = "PROJECT_ID"),
        @Index(name = "IX_ACTIVITY_OCCURRED", columnList = "OCCURRED_AT")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "project"})
@EqualsAndHashCode(of = "id")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activity_logs_seq")
    @SequenceGenerator(name = "activity_logs_seq", sequenceName = "SEQ_ACTIVITY_LOGS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION", nullable = false, length = 20)
    private ActivityAction action;

    /** Nombre simple de la entidad afectada: "Task", "Board", "Project"... */
    @Size(max = 60)
    @Column(name = "ENTITY_TYPE", length = 60)
    private String entityType;

    @Column(name = "ENTITY_ID")
    private Long entityId;

    @Size(max = 1000)
    @Column(name = "DETAILS", length = 1000)
    private String details;

    /** Autor de la accion. Nulo si la genero un proceso automatico (p. ej. la IA). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", foreignKey = @ForeignKey(name = "FK_ACTIVITY_USER"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PROJECT_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ACTIVITY_PROJECT"))
    private Project project;

    @Column(name = "OCCURRED_AT", nullable = false, updatable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();
}
