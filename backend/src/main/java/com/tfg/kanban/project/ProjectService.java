package com.tfg.kanban.project;

import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.repository.ProjectRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.project.dto.ProjectResponse;
import com.tfg.kanban.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura del proyecto que hay debajo de cada workspace. Crearlo, editarlo y
 * borrarlo lo hace {@code WorkspaceService}, junto con su equipo y su tablero.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;

    public ProjectResponse findById(Long projectId) {
        Project project = getProject(projectId);
        return ProjectResponse.from(project, roleIn(project));
    }

    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto", projectId));
    }

    private Role roleIn(Project project) {
        return teamMemberRepository.findRoleByProject(project.getId(), SecurityUtils.requireCurrentUserId()).orElse(null);
    }
}
