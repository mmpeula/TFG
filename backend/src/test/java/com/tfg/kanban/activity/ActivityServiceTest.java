package com.tfg.kanban.activity;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.ActivityLog;
import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.ActivityLogRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActivityService activityService;

    private final Project project = Project.builder().id(1L).name("Practica").build();

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private ActivityLog captured() {
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("La entrada se atribuye al usuario autenticado")
    void recordsWithCurrentUser() {
        TestSecurity.authenticateAs(4L, "MIEMBRO");
        User author = User.builder().id(4L).fullName("Luis Perez").build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(author));

        activityService.record(project, ActivityAction.CREACION, "Task", 9L, "Creo la tarea X");

        ActivityLog log = captured();
        assertThat(log.getUser()).isEqualTo(author);
        assertThat(log.getAction()).isEqualTo(ActivityAction.CREACION);
        assertThat(log.getEntityType()).isEqualTo("Task");
        assertThat(log.getEntityId()).isEqualTo(9L);
        assertThat(log.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Sin usuario autenticado la entrada queda sin autor (proceso automatico)")
    void recordsWithoutUser() {
        activityService.record(project, ActivityAction.GENERACION_IA, "Project", 1L, "Genero la planificacion");

        assertThat(captured().getUser()).isNull();
    }

    @Test
    @DisplayName("Un detalle demasiado largo se recorta para caber en la columna")
    void truncatesLongDetails() {
        activityService.record(project, ActivityAction.ACTUALIZACION, "Task", 1L, "x".repeat(1500));

        String details = captured().getDetails();
        assertThat(details).hasSize(1000).endsWith("...");
    }

    @Test
    @DisplayName("Un detalle nulo o corto se guarda tal cual")
    void keepsShortDetails() {
        activityService.record(project, ActivityAction.BORRADO, "Task", 1L, null);

        assertThat(captured().getDetails()).isNull();
    }
}
