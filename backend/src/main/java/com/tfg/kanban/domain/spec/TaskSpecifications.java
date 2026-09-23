package com.tfg.kanban.domain.spec;

import com.tfg.kanban.domain.model.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de busqueda de tareas.
 *
 * Dos detalles importantes de Oracle que condicionan como esta escrito esto:
 * <ul>
 *   <li>No se usan consultas JPQL con condiciones {@code (:param is null or ...)}:
 *       Oracle no puede inferir el tipo de un parametro que solo se compara con NULL.</li>
 *   <li>La pertenencia al equipo se comprueba con un EXISTS en lugar de con un JOIN
 *       + DISTINCT, porque {@code Task.description} es un CLOB y Oracle no admite
 *       CLOB como clave de comparacion en un SELECT DISTINCT (ORA-22848).</li>
 * </ul>
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> search(Long userId, Long projectId, Long boardId,
                                             Long columnId, Long assigneeId, TaskPriority priority) {
        return (root, query, cb) -> {
            // Todos los JOIN son to-one, asi que no multiplican filas.
            Join<Task, BoardColumn> column = root.join("boardColumn");
            Join<BoardColumn, Board> board = column.join("board");
            Join<Board, Project> project = board.join("project");

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(isTeamMember(cb, query, project, userId));
            // Un workspace suspendido no se puede abrir: GET /api/tasks/{id} lo
            // rechaza porque el permiso por tarea filtra por enabled. Sin esta linea
            // la busqueda era la puerta de atras que devolvia sus tareas igualmente.
            predicates.add(cb.isTrue(project.get("enabled")));

            if (projectId != null) {
                predicates.add(cb.equal(project.get("id"), projectId));
            }
            if (boardId != null) {
                predicates.add(cb.equal(board.get("id"), boardId));
            }
            if (columnId != null) {
                predicates.add(cb.equal(column.get("id"), columnId));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** EXISTS (select 1 from TeamMember m where m.team = project.team and m.user.id = :userId). */
    private static Predicate isTeamMember(CriteriaBuilder cb, CommonAbstractCriteria query,
                                          Path<Project> project, Long userId) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<TeamMember> member = subquery.from(TeamMember.class);
        subquery.select(cb.literal(1))
                .where(cb.equal(member.get("team"), project.get("team")),
                        cb.equal(member.get("user").get("id"), userId));
        return cb.exists(subquery);
    }
}
