package com.tfg.kanban.domain.repository;

import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTask_IdOrderByCreatedAtAsc(Long taskId);

    long countByTask_Id(Long taskId);

    /** Numero de adjuntos de cada tarea del lote, en una sola consulta. */
    @Query("""
            select new com.tfg.kanban.common.CountByEntity(a.task.id, count(a))
            from Attachment a where a.task.id in :taskIds group by a.task.id
            """)
    List<CountByEntity> countByTaskIds(Collection<Long> taskIds);
}
