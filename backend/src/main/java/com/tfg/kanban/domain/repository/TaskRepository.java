package com.tfg.kanban.domain.repository;

import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByBoardColumn_IdOrderByPositionAsc(Long columnId);

    List<Task> findByBoardColumn_Board_IdOrderByPositionAsc(Long boardId);

    long countByBoardColumn_Id(Long columnId);

    /** Tareas por columna para un lote de columnas, en una sola consulta. */
    @Query("""
            select new com.tfg.kanban.common.CountByEntity(t.boardColumn.id, count(t))
            from Task t where t.boardColumn.id in :columnIds group by t.boardColumn.id
            """)
    List<CountByEntity> countByColumnIds(Collection<Long> columnIds);

    /**
     * Tareas de esta persona con estimacion y horas reales apuntadas, en
     * cualquier proyecto en el que haya trabajado (no solo el actual): es su
     * ritmo como persona, no del proyecto. Sirve para calibrar el proximo reparto.
     */
    @Query("""
            select t from Task t join t.assignees a
            where a.id = :userId and t.estimatedHours is not null and t.actualHours is not null
            """)
    List<Task> findWithActualsByAssignee(Long userId);
}
