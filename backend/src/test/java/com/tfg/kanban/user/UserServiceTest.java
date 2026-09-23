package com.tfg.kanban.user;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
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
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de las reglas de cuentas. El conteo global de administradores
 * ({@code countByRole}) se prueba aqui con un repositorio simulado, no con el
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

    @InjectMocks
    private UserService userService;

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Degradar al ultimo ADMIN de la plataforma no esta permitido")
    void cannotDemoteLastAdmin() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateGlobalRole(1L, new UpdateGlobalRoleRequest(Role.MIEMBRO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("al menos un administrador");
    }

    @Test
    @DisplayName("Con mas de un ADMIN, degradar a uno de ellos si esta permitido")
    void canDemoteAdminIfNotTheLastOne() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        var response = userService.updateGlobalRole(1L, new UpdateGlobalRoleRequest(Role.MIEMBRO));

        assertThat(response.role()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Ascender a alguien a ADMIN no comprueba el ultimo administrador")
    void promotingToAdminDoesNotCheckTheGuard() {
        TestSecurity.authenticateAs(9L, "ADMIN");
        User member = User.builder().id(2L).email("luis@uni.es").role(Role.MIEMBRO).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        var response = userService.updateGlobalRole(2L, new UpdateGlobalRoleRequest(Role.ADMIN));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Deshabilitar al ultimo ADMIN de la plataforma no esta permitido")
    void cannotDisableLastAdmin() {
        // Quien deshabilita no es el propio admin, para llegar a la comprobacion
        // del ultimo administrador y no a la de "no puedes darte de baja a ti mismo".
        TestSecurity.authenticateAs(9L, "ADMIN");
        User admin = User.builder().id(1L).email("admin@planifia.local").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.disable(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("al menos un administrador");
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
                .hasMessageContaining("contrasena actual");

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
}
