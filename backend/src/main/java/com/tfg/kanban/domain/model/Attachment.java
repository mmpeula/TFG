package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Fichero adjunto a una tarea.
 *
 * En la base de datos solo se guardan los metadatos; el contenido va al
 * directorio configurado en {@code app.storage.upload-dir} (volumen de Docker).
 */
@Entity
@Table(name = "ATTACHMENTS", indexes = @Index(name = "IX_ATTACHMENTS_TASK", columnList = "TASK_ID"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"task", "uploadedBy"})
@EqualsAndHashCode(of = "id")
public class Attachment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attachments_seq")
    @SequenceGenerator(name = "attachments_seq", sequenceName = "SEQ_ATTACHMENTS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    /** Nombre con el que el usuario subio el fichero (se usa al descargarlo). */
    @NotBlank
    @Column(name = "FILENAME", nullable = false, length = 255)
    private String filename;

    /** Nombre aleatorio con el que se guarda en disco, para evitar colisiones y path traversal. */
    @NotBlank
    @Column(name = "STORED_NAME", nullable = false, length = 120)
    private String storedName;

    @Column(name = "CONTENT_TYPE", length = 120)
    private String contentType;

    @Column(name = "SIZE_BYTES", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TASK_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ATTACH_TASK"))
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UPLOADED_BY_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ATTACH_USER"))
    private User uploadedBy;
}
