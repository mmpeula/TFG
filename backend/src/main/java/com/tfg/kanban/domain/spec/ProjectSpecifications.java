package com.tfg.kanban.domain.spec;

import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.TeamMember;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Proyectos visibles para un usuario.
 *
 * Igual que en {@link TaskSpecifications}, la pertenencia al equipo se resuelve con
 * un EXISTS: {@code Project.briefing} es un CLOB y Oracle no permite DISTINCT sobre
 * columnas CLOB (ORA-22848).
 */
public final class ProjectSpecifications {

    private ProjectSpecifications() {
    }

    public static Specification<Project> visibleTo(Long userId) {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<TeamMember> member = subquery.from(TeamMember.class);
            subquery.select(cb.literal(1))
                    .where(cb.equal(member.get("team"), root.get("team")),
                            cb.equal(member.get("user").get("id"), userId));
            return cb.exists(subquery);
        };
    }
}
