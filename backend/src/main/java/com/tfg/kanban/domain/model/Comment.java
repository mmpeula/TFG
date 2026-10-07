package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Comentario de un usuario sobre una tarea.
 *
 * La tabla se llama COMMENTS: COMMENT es palabra reservada en Oracle.
 */
@Entity
@Table(name = "COMMENTS", indexes = @Index(name = "IX_COMMENTS_TASK", columnList = "TASK_ID"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"task", "author"})
@EqualsAndHashCode(of = "id", callSuper = false)
public class Comment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comments_seq")
    @SequenceGenerator(name = "comments_seq", sequenceName = "SEQ_COMMENTS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Size(max = 2000)
    @Column(name = "CONTENT", nullable = false, length = 2000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TASK_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_COMMENTS_TASK"))
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "AUTHOR_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_COMMENTS_AUTHOR"))
    private User author;

    /** true si el comentario se ha editado despues de crearlo. */
    @Column(name = "EDITED", nullable = false)
    @Builder.Default
    private boolean edited = false;
}
