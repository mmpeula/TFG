package com.tfg.kanban.user;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.task.TaskService;
import com.tfg.kanban.user.dto.UpdateGlobalRoleRequest;
import com.tfg.kanban.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de las reglas de cuentas. El conteo global de administradores
 * ({@code countByRoleAndEnabledTrue}) se prueba aqui con un repositorio simulado, no con el
 * Oracle real de los tests de integracion: ese Oracle es compartido entre todas
 * las clases de test (vease {@code AbstractOracleContainerTest}) y siempre tiene
 * al menos el administrador que crea {@link AdminBootstrapRunner} al arrancar,
 * asi que "queda un unico ADMIN en toda la base de datos" no es un escenario que
 * se pueda reproducir de forma fiable con datos reales.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TaskService taskService;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Un alumno puede pasar a docente o a administrador, y un docente a administrador")
    void rolesOnlyGoUp() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User student = User.builder().id(2L).email("luis@uni.es").role(Role.MIEMBRO).build();
        User otherStudent = User.builder().id(4L).email("eva@uni.es").role(Role.MIEMBRO).build();
        User teacher = User.builder().id(3L).email("marta@uni.es").role(Role.DOCENTE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(userRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherStudent));

        assertThat(userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.DOCENTE)).role()).isEqualTo(Role.DOCENTE);
        assertThat(userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.ADMIN)).role()).isEqualTo(Role.ADMIN);
        assertThat(userService.updateGlobalRole(3L, new UpdateGlobalRoleRequest(Role.ADMIN)).role()).isEqualTo(Role.ADMIN);
        assertThat(userService.updateGlobalRole(4L, new UpdateGlobalRoleRequest(Role.ADMIN)).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Pedir el rol que la cuenta ya tiene no cambia nada")
    void sameRoleIsANoOp() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User student = User.builder().id(2L).email("luis@uni.es").role(Role.MIEMBRO).build();
        User teacher = User.builder().id(3L).email("marta@uni.es").role(Role.DOCENTE).build();
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(userRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThat(userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.MIEMBRO)).role()).isEqualTo(Role.MIEMBRO);
        assertThat(userService.updateGlobalRole(3L, new UpdateGlobalRoleRequest(Role.DOCENTE)).role()).isEqualTo(Role.DOCENTE);
        assertThat(userService.updateGlobalRole(1L, new UpdateGlobalRoleRequest(Role.ADMIN)).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Un docente no vuelve a ser alumno, y un ADMIN no se degrada, haya uno o varios")
    void rolesNeverGoDown() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User teacher = User.builder().id(3L).email("marta@uni.es").role(Role.DOCENTE).build();
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(3L);

        assertThatThrownBy(() -> userService.updateGlobalRole(3L, new UpdateGlobalRoleRequest(Role.MIEMBRO)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no puede volver a ser alumno");
        assertThatThrownBy(() -> userService.updateGlobalRole(1L, new UpdateGlobalRoleRequest(Role.MIEMBRO)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no se puede degradar");
        assertThatThrownBy(() -> userService.updateGlobalRole(1L, new UpdateGlobalRoleRequest(Role.DOCENTE)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(teacher.getRole()).isEqualTo(Role.DOCENTE);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("RESPONSABLE es un rol de equipo: no se puede dar como rol de cuenta")
    void responsableIsNotAnAccountRole() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User student = User.builder().id(2L).email("luis@uni.es").role(Role.MIEMBRO).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.RESPONSABLE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("rol de equipo");
        assertThat(student.getRole()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Deshabilitar al ultimo ADMIN habilitado no esta permitido")
    void cannotDisableLastAdmin() {
        // Quien deshabilita no es el propio admin, para llegar a la comprobacion
        // del ultimo administrador y no a la de "no puedes darte de baja a ti mismo".
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.disable(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("al menos un administrador");
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Con otro ADMIN habilitado, dar de baja a uno si esta permitido")
    void canDisableAdminIfAnotherRemains() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("ana@uni.es").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);

        userService.disable(1L);

        assertThat(admin.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Un ADMIN ya deshabilitado no cuenta: darlo de baja otra vez no se bloquea por ser el ultimo")
    void disablingAnAlreadyDisabledAdminIsNotBlocked() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("vieja@uni.es").role(Role.ADMIN).enabled(false).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        userService.disable(1L);

        assertThat(admin.isEnabled()).isFalse();
        // Sus tareas ya se soltaron cuando se dio de baja la primera vez.
        verify(taskService, never()).releaseOpenTasksOf(any());
    }

    @Test
    @DisplayName("Cambiarse la contrasena propia exige acertar la actual")
    void changingOwnPasswordNeedsTheCurrentOne() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        User user = User.builder().id(2L).email("luis@uni.es").passwordHash("hash-de-ahora").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("noEsLaSuya", "hash-de-ahora")).thenReturn(false);

        assertThatThrownBy(() -> userService.update(2L,
                new UpdateUserRequest("Luis", null, "nuevaClave456")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("contraseña actual");

        assertThatThrownBy(() -> userService.update(2L,
                new UpdateUserRequest("Luis", "noEsLaSuya", "nuevaClave456")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-de-ahora");
    }

    @Test
    @DisplayName("Con la contrasena actual correcta, el cambio se aplica")
    void changingOwnPasswordWorksWithTheCurrentOne() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        User user = User.builder().id(2L).email("luis@uni.es").passwordHash("hash-de-ahora").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("laDeAhora", "hash-de-ahora")).thenReturn(true);
        when(passwordEncoder.encode("nuevaClave456")).thenReturn("hash-nuevo");

        userService.update(2L, new UpdateUserRequest("Luis", "laDeAhora", "nuevaClave456"));

        assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
    }

    @Test
    @DisplayName("El administrador restablece la de otra persona sin saber la suya")
    void adminResetsSomeoneElsesPasswordWithoutKnowingIt() {
        // No se le pide porque no la tiene: para eso existe el restablecimiento.
        TestSecurity.authenticateAs(9L, "ADMIN");
        User user = User.builder().id(2L).email("luis@uni.es").passwordHash("hash-de-ahora").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("claveRestablecida9")).thenReturn("hash-restablecido");

        userService.update(2L, new UpdateUserRequest("Luis", null, "claveRestablecida9"));

        assertThat(user.getPasswordHash()).isEqualTo("hash-restablecido");
    }

    @Test
    @DisplayName("Editar solo el nombre no pide ninguna contrasena")
    void renamingNeedsNoPassword() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        User user = User.builder().id(2L).email("luis@uni.es").passwordHash("hash-de-ahora").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        var response = userService.update(2L, new UpdateUserRequest("Luis Perez", null, null));

        assertThat(response.fullName()).isEqualTo("Luis Perez");
        assertThat(user.getPasswordHash()).isEqualTo("hash-de-ahora");
    }

    @Test
    @DisplayName("Reactivar una cuenta la deja habilitada de nuevo")
    void enableReactivatesAccount() {
        User user = User.builder().id(2L).email("luis@uni.es").enabled(false).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.enable(2L);

        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Dar de baja una cuenta suelta sus tareas sin terminar")
    void disablingReleasesOpenTasks() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User user = User.builder().id(2L).email("carla@uni.es").fullName("Carla Ruiz").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.disable(2L);

        verify(taskService).releaseOpenTasksOf(user);
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("No se asciende a administrador a quien esta en algun espacio de trabajo")
    void cannotPromoteToAdminWhileInATeam() {
        User user = User.builder().id(2L).email("ana@uni.es").role(Role.MIEMBRO).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(teamMemberRepository.countByUser_Id(2L)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.ADMIN)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Esta cuenta está en 1 espacio de trabajo");
        assertThat(user.getRole()).isEqualTo(Role.MIEMBRO);

        // A docente si: sigue pudiendo estar en sus equipos.
        userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.DOCENTE));
        assertThat(user.getRole()).isEqualTo(Role.DOCENTE);
    }
}
