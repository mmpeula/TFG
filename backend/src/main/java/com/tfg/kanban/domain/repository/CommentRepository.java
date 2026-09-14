package com.tfg.kanban.domain.repository;

import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByTask_IdOrderByCreatedAtAsc(Long taskId, Pageable pageable);

    long countByTask_Id(Long taskId);

    /** Numero de comentarios de cada tarea del lote, en una sola consulta. */
    @Query("""
            select new com.tfg.kanban.common.CountByEntity(c.task.id, count(c))
            from Comment c where c.task.id in :taskIds group by c.task.id
            """)
    List<CountByEntity> countByTaskIds(Collection<Long> taskIds);
}
