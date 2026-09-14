package com.tfg.kanban;

import com.tfg.kanban.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion de referencia: comprueba que el contexto de Spring arranca
 * contra un Oracle real y que el esquema JPA se ha podido crear.
 *
 * Se llama *IT para que lo ejecute failsafe (fase verify) y no surefire (fase test),
 * de modo que {@code ./mvnw test} siga funcionando sin Docker.
 */
@SpringBootTest
class ApplicationContextIT extends AbstractOracleContainerTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("El contexto de Spring arranca con la base de datos Oracle del contenedor")
    void contextLoads() throws SQLException {
        assertThat(ORACLE.isRunning()).isTrue();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("Oracle");
        }
    }

    @Test
    @DisplayName("Las tablas generadas por JPA son accesibles desde los repositorios")
    void schemaIsCreated() {
        // No se comprueba un numero concreto de filas: otros tests de la misma
        // ejecucion comparten el contenedor y pueden haber insertado datos.
        assertThat(userRepository.count()).isNotNegative();
    }
}
