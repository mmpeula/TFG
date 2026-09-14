package com.tfg.kanban;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para los tests de integracion.
 *
 * Levanta un Oracle Free real con Testcontainers y lo conecta al contexto de Spring
 * mediante {@code @ServiceConnection} (no hace falta configurar el datasource a mano).
 *
 * <p>Se usa el patron <i>singleton container</i>: el contenedor se arranca una sola
 * vez en el bloque estatico y se comparte entre TODAS las clases de test de la
 * ejecucion. No se usan {@code @Testcontainers} ni {@code @Container} a proposito,
 * porque esa extension para el contenedor al terminar cada clase mientras Spring
 * sigue reutilizando el mismo contexto (y su pool de conexiones), lo que dejaba a
 * los tests siguientes hablando con una base de datos que ya no existia. El
 * contenedor lo retira Ryuk al acabar la JVM.</p>
 *
 * Requiere Docker en la maquina que ejecuta los tests.
 */
@ActiveProfiles("test")
public abstract class AbstractOracleContainerTest {

    /** Imagen ligera y de arranque rapido; la misma familia que la de docker-compose. */
    private static final DockerImageName ORACLE_IMAGE =
            DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart");

    // El nombre del PDB (FREEPDB1) lo fija la imagen, por eso no se configura aqui.
    @ServiceConnection
    protected static final OracleContainer ORACLE = new OracleContainer(ORACLE_IMAGE)
            .withUsername("kanban")
            .withPassword("kanban");

    static {
        ORACLE.start();
    }
}
