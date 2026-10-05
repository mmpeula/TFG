package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoard_IdOrderByPositionAsc(Long boardId);

    /** Columnas de un lote de proyectos, para pintar la pantalla de workspaces sin N+1. */
    @Query("""
            select c from BoardColumn c
            join c.board b
            where b.project.id in :projectIds
            order by b.id asc, c.position asc
            """)
    List<BoardColumn> findByProjectIds(Collection<Long> projectIds);

    /**
     * Lo mismo pero como tuplas (proyecto, tablero, columna, posicion): no carga
     * entidades, asi que pintar la lista de workspaces no dispara consultas sueltas.
     */
    @Query("""
            select b.project.id, b.id, c.id, c.position
            from BoardColumn c join c.board b
            where b.project.id in :projectIds
            order by b.id asc, c.position asc
            """)
    List<Object[]> findColumnRowsByProjectIds(Collection<Long> projectIds);
}
