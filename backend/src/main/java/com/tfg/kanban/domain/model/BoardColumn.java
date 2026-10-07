package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Columna (lista) de un tablero Kanban: "Por hacer", "En curso", "Hecho"...
 *
 * La tabla se llama BOARD_COLUMNS y el orden se guarda en POSITION_INDEX
 * porque COLUMN es palabra reservada en Oracle.
 */
@Entity
@Table(name = "BOARD_COLUMNS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"tasks"})
@EqualsAndHashCode(of = "id", callSuper = false)
public class BoardColumn extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "board_columns_seq")
    @SequenceGenerator(name = "board_columns_seq", sequenceName = "SEQ_BOARD_COLUMNS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** Posicion de la columna dentro del tablero (0 = primera, de izquierda a derecha). */
    @Min(0)
    @Column(name = "POSITION_INDEX", nullable = false)
    @Builder.Default
    private Integer position = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BOARD_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_COLUMNS_BOARD"))
    private Board board;

    @OneToMany(mappedBy = "boardColumn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();
}
