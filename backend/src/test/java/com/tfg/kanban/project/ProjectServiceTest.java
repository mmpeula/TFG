package com.tfg.kanban.project;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.ProjectStatus;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.domain.repository.ProjectRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.project.dto.CreateProjectRequest;
import com.tfg.kanban.project.dto.UpdateProjectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ActivityService activityService;

    @InjectMocks
    private ProjectService projectService;

    private Team team;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "RESPONSABLE");
        team = Team.builder().id(3L).name("Grupo 4").build();
        when(teamRepository.findById(3L)).thenReturn(Optional.of(team));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(9L);
            return p;
        });
        when(teamMemberRepository.findRoleByProject(9L, 1L)).thenReturn(Optional.of(Role.RESPONSABLE));
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Un proyecto nuevo nace en estado PLANIFICACION")
    void createStartsInPlanificacion() {
        var request = new CreateProjectRequest(3L, "  Practica 2  ", "desc", "guion", LocalDate.of(2026, 12, 15));

        var response = projectService.create(request);

        assertThat(response.name()).isEqualTo("Practica 2");
        assertThat(response.status()).isEqualTo(ProjectStatus.PLANIFICACION);
        assertThat(response.myRole()).isEqualTo(Role.RESPONSABLE);
    }

    @Test
    @DisplayName("No se admite otro proyecto con el mismo nombre en el equipo")
    void rejectsDuplicatedName() {
        when(projectRepository.existsByTeam_IdAndNameIgnoreCase(3L, "Practica 2")).thenReturn(true);
        var request = new CreateProjectRequest(3L, "Practica 2", null, null, null);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe un proyecto");
    }

    @Test
    @DisplayName("Crear un proyecto en un equipo inexistente da 404")
    void rejectsUnknownTeam() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());
        var request = new CreateProjectRequest(99L, "Practica", null, null, null);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Al editar, omitir el estado lo deja como estaba")
    void updateKeepsStatusWhenOmitted() {
        Project project = Project.builder().id(9L).name("Practica").team(team)
                .status(ProjectStatus.EN_CURSO).build();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));

        var response = projectService.update(9L, new UpdateProjectRequest("Practica 2", null, null, null, null));

        assertThat(response.status()).isEqualTo(ProjectStatus.EN_CURSO);
        assertThat(response.name()).isEqualTo("Practica 2");
    }

    @Test
    @DisplayName("Al editar, un estado explicito sustituye al anterior")
    void updateChangesStatusWhenProvided() {
        Project project = Project.builder().id(9L).name("Practica").team(team)
                .status(ProjectStatus.PLANIFICACION).build();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));

        var response = projectService.update(9L,
                new UpdateProjectRequest("Practica", null, null, null, ProjectStatus.ENTREGADO));

        assertThat(response.status()).isEqualTo(ProjectStatus.ENTREGADO);
    }

    @Test
    @DisplayName("Buscar un proyecto inexistente da 404")
    void findByIdNotFound() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Proyecto");
    }
}
