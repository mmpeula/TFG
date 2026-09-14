package com.tfg.kanban.domain.repository;

import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    Optional<TeamMember> findByTeam_IdAndUser_Id(Long teamId, Long userId);

    List<TeamMember> findByTeam_IdOrderByUser_FullNameAsc(Long teamId);

    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);

    long countByTeam_IdAndRole(Long teamId, com.tfg.kanban.domain.model.Role role);

    /** Cuantos miembros tiene cada equipo del lote. Evita una consulta por workspace. */
    @Query("""
            select new com.tfg.kanban.common.CountByEntity(m.team.id, count(m))
            from TeamMember m where m.team.id in :teamIds group by m.team.id
            """)
    List<CountByEntity> countByTeamIds(Collection<Long> teamIds);

    /** Rol del usuario en cada uno de esos equipos, en una sola consulta. */
    @Query("""
            select m.team.id, m.role from TeamMember m
            where m.team.id in :teamIds and m.user.id = :userId
            """)
    List<Object[]> rolesInTeams(Collection<Long> teamIds, Long userId);

    /** Rol del usuario en el equipo al que pertenece un proyecto. */
    @Query("""
            select m.role from TeamMember m
            join Project p on p.team = m.team
            where p.id = :projectId and m.user.id = :userId
            """)
    Optional<com.tfg.kanban.domain.model.Role> findRoleByProject(Long projectId, Long userId);

    /** Rol del usuario en el equipo al que pertenece un tablero. */
    @Query("""
            select m.role from TeamMember m
            join Project p on p.team = m.team
            join Board b on b.project = p
            where b.id = :boardId and m.user.id = :userId
            """)
    Optional<com.tfg.kanban.domain.model.Role> findRoleByBoard(Long boardId, Long userId);

    /** Rol del usuario en el equipo al que pertenece una columna. */
    @Query("""
            select m.role from TeamMember m
            join Project p on p.team = m.team
            join Board b on b.project = p
            join BoardColumn c on c.board = b
            where c.id = :columnId and m.user.id = :userId
            """)
    Optional<com.tfg.kanban.domain.model.Role> findRoleByColumn(Long columnId, Long userId);

    /** Rol del usuario en el equipo al que pertenece una tarea. */
    @Query("""
            select m.role from TeamMember m
            join Project p on p.team = m.team
            join Board b on b.project = p
            join BoardColumn c on c.board = b
            join Task t on t.boardColumn = c
            where t.id = :taskId and m.user.id = :userId
            """)
    Optional<com.tfg.kanban.domain.model.Role> findRoleByTask(Long taskId, Long userId);
}
