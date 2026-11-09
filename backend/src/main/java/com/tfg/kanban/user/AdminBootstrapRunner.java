package com.tfg.kanban.user;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Deja siempre una cuenta ADMIN lista para entrar, porque ese rol no se puede
 * pedir al registrarse (RF-01) ni conceder desde la aplicacion sin que ya
 * exista un administrador antes: sin este arranque, nadie podria llegar nunca
 * al primero.
 *
 * <p>Si ya hay algun ADMIN que pueda entrar no hace nada: uno dado de baja no
 * cuenta, porque la plataforma se quedaria sin nadie que la administre. Si el correo configurado en
 * {@link AdminProperties} pertenece a una cuenta existente, esa cuenta se
 * asciende (y se reactiva si estaba deshabilitada) en vez de duplicarla.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(adminProperties.getPassword())) {
            log.warn("No hay ningun administrador y no se ha creado ninguno: falta ADMIN_PASSWORD en la configuracion");
            return;
        }

        String email = adminProperties.getEmail().trim().toLowerCase();
        User admin = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> User.builder()
                .email(email)
                .fullName("Administrador")
                .passwordHash(passwordEncoder.encode(adminProperties.getPassword()))
                .build());

        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);

        log.info("Cuenta de administrador lista: {}", email);
    }
}
