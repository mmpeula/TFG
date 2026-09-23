package com.tfg.kanban.project;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.ProjectRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.domain.spec.ProjectSpecifications;
import com.tfg.kanban.project.dto.CreateProjectRequest;
import com.tfg.kanban.project.dto.ProjectResponse;
import com.tfg.kanban.project.dto.UpdateProjectRequest;
import com.tfg.kanban.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ActivityService activityService;

    public PageResponse<ProjectResponse> findVisible(Long teamId, ProjectStatus status, Pageable pageable) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return PageResponse.of(
                projectRepository.findAll(ProjectSpecifications.visibleTo(userId, teamId, status), pageable),
                project -> ProjectResponse.from(project, roleIn(project)));
    }

    public ProjectResponse findById(Long projectId) {
        Project project = getProject(projectId);
        return ProjectResponse.from(project, roleIn(project));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", request.teamId()));

        if (projectRepository.existsByTeam_IdAndNameIgnoreCase(team.getId(), request.name().trim())) {
            throw new BusinessRuleException("Ya existe un proyecto con ese nombre en el equipo");
        }

        Project project = Project.builder()
                .name(request.name().trim())
                .description(request.description())
                .briefing(request.briefing())
                .deadline(request.deadline())
                .status(ProjectStatus.PLANIFICACION)
                .team(team)
                .build();

        Project saved = projectRepository.save(project);
        activityService.record(saved, ActivityAction.CREACION, "Project", saved.getId(),
                "Creo el proyecto '%s'".formatted(saved.getName()));

        return ProjectResponse.from(saved, roleIn(saved));
    }

    @Transactional
    public ProjectResponse update(Long projectId, UpdateProjectRequest request) {
        Project project = getProject(projectId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setBriefing(request.briefing());
        project.setDeadline(request.deadline());
        if (request.status() != null) {
            project.setStatus(request.status());
        }

        activityService.record(project, ActivityAction.ACTUALIZACION, "Project", project.getId(),
                "Actualizo el proyecto '%s'".formatted(project.getName()));

        return ProjectResponse.from(project, roleIn(project));
    }

    @Transactional
    public void delete(Long projectId) {
        Project project = getProject(projectId);
        // Los tableros, columnas, tareas y el historial se borran en cascada.
        projectRepository.delete(project);
    }

    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto", projectId));
    }

    private Role roleIn(Project project) {
        return teamMemberRepository.findRoleByProject(project.getId(), SecurityUtils.requireCurrentUserId()).orElse(null);
    }

}
