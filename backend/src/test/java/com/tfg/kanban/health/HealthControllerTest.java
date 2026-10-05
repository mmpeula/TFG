package com.tfg.kanban.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** El health check debe distinguir bien los tres escenarios: BD sana, invalida y caida. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthControllerTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData metaData;

    private MockEnvironment environment;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        controller = new HealthController(dataSource, environment);
        ReflectionTestUtils.setField(controller, "applicationName", "planifia-backend");
    }

    @Test
    @DisplayName("Con la base de datos sana responde 200 y UP con el motor y la version")
    void reportsUp() throws SQLException {
        environment.setActiveProfiles("docker");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("Oracle");
        when(metaData.getDatabaseProductVersion()).thenReturn("23.26.2.0.0");

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().profile()).isEqualTo("docker");
        assertThat(response.getBody().application()).isEqualTo("planifia-backend");
        assertThat(response.getBody().database().product()).isEqualTo("Oracle");
        assertThat(response.getBody().database().version()).isEqualTo("23.26.2.0.0");
    }

    @Test
    @DisplayName("Si la conexion no es valida responde 503 y DOWN")
    void reportsDownWhenConnectionInvalid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(false);

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo("DOWN");
        assertThat(response.getBody().database().status()).isEqualTo("DOWN");
        assertThat(response.getBody().database().error()).contains("no es válida");
    }

    @Test
    @DisplayName("Si la base de datos no responde, se informa sin filtrar el detalle del error")
    void reportsDownWhenSqlFails() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("ORA-12541: TNS:no listener en bd:1521"));

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().database().status()).isEqualTo("DOWN");
        // El endpoint es publico: el mensaje de Oracle (con host y puerto) se queda
        // en el log y fuera solo sale que la base de datos no responde.
        assertThat(response.getBody().database().error()).isEqualTo("La base de datos no responde");
        assertThat(response.getBody().database().error()).doesNotContain("ORA-12541");
        // Sin perfiles activos se informa de "default"
        assertThat(response.getBody().profile()).isEqualTo("default");
    }
}
