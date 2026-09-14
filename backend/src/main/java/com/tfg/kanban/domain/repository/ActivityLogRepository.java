package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByProject_IdOrderByOccurredAtDesc(Long projectId, Pageable pageable);

    /** Ultimo movimiento de cada proyecto del lote: alimenta la tarjeta del workspace. */
    @Query("""
            select a.project.id, max(a.occurredAt) from ActivityLog a
            where a.project.id in :projectIds group by a.project.id
            """)
    List<Object[]> lastActivityByProjects(Collection<Long> projectIds);

    List<ActivityLog> findTop1ByProject_IdOrderByOccurredAtDesc(Long projectId);
}
