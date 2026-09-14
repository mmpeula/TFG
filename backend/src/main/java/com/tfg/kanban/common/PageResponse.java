package com.tfg.kanban.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginacion propio.
 *
 * Se usa en lugar de devolver {@code Page} directamente porque la serializacion de
 * {@code PageImpl} no es estable entre versiones de Spring Data y ademas expone
 * detalles internos que el frontend no necesita.
 */
@Schema(description = "Pagina de resultados")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "37") long totalElements,
        @Schema(example = "2") int totalPages,
        boolean first,
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
