package com.tfg.kanban.project;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.ProjectStatus;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.domain.repository.ProjectRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("El detalle lleva el rol de quien lo consulta en ese equipo")
    void findByIdCarriesTheRoleOfTheViewer() {
        Team team = Team.builder().id(3L).name("Grupo 4").build();
        Project project = Project.builder().id(9L).name("Practica 2").status(ProjectStatus.PLANIFICACION)
                .team(team).build();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(teamMemberRepository.findRoleByProject(9L, 1L)).thenReturn(Optional.of(Role.RESPONSABLE));

        var response = projectService.findById(9L);

        assertThat(response.name()).isEqualTo("Practica 2");
        assertThat(response.myRole()).isEqualTo(Role.RESPONSABLE);
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
