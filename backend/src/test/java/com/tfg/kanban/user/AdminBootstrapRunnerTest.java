package com.tfg.kanban.user;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La plataforma nunca se queda sin un administrador que pueda entrar. */
class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminProperties properties = new AdminProperties();
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        properties.setEmail("Admin@Planifia.local");
        properties.setPassword("clave-inicial");
        when(passwordEncoder.encode("clave-inicial")).thenReturn("hash");
        runner = new AdminBootstrapRunner(userRepository, passwordEncoder, properties);
    }

    @Test
    @DisplayName("Con un ADMIN habilitado no hace nada")
    void doesNothingWhenAnActiveAdminExists() {
        when(userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(true);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin ningun ADMIN crea la cuenta configurada")
    void createsTheConfiguredAdmin() {
        when(userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("admin@planifia.local")).thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@planifia.local");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("Si todos los ADMIN estan dados de baja, la cuenta configurada se asciende y se reactiva")
    void promotesAndReactivatesWhenAllAdminsAreDisabled() {
        when(userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(false);
        User existing = User.builder().id(7L).email("admin@planifia.local").role(Role.MIEMBRO).enabled(false)
                .passwordHash("hash-que-ya-tenia").build();
        when(userRepository.findByEmailIgnoreCase("admin@planifia.local")).thenReturn(Optional.of(existing));

        runner.run(null);

        verify(userRepository).save(existing);
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getPasswordHash()).isEqualTo("hash-que-ya-tenia");
    }

    @Test
    @DisplayName("Sin contrasena configurada no se crea ningun administrador con una clave conocida")
    void withoutPasswordNoAdminIsCreated() {
        properties.setPassword(" ");
        when(userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(false);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }
}
