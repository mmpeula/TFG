package com.tfg.kanban.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;

/**
 * Health check publico de la API.
 *
 * Verifica de verdad la conexion con la base de datos (abre una conexion del pool
 * y lee sus metadatos), de forma que sirve como prueba end-to-end del arranque.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Comprobacion de estado de la aplicacion")
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final Environment environment;

    @Value("${spring.application.name:planifia-backend}")
    private String applicationName;

    @Operation(summary = "Estado de la aplicacion",
            description = "Devuelve UP si la aplicacion responde y la conexion con la base de datos es valida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La aplicacion y la base de datos responden"),
            @ApiResponse(responseCode = "503", description = "La base de datos no esta disponible")
    })
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse.DatabaseStatus database = checkDatabase();
        boolean up = "UP".equals(database.status());

        HealthResponse body = new HealthResponse(
                up ? "UP" : "DOWN",
                applicationName,
                activeProfile(),
                Instant.now(),
                database
        );

        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private HealthResponse.DatabaseStatus checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return new HealthResponse.DatabaseStatus("DOWN", null, null, "La conexión no es válida");
            }
            DatabaseMetaData metaData = connection.getMetaData();
            return new HealthResponse.DatabaseStatus(
                    "UP", metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion(), null);
        } catch (SQLException ex) {
            // El detalle va al log y no a la respuesta: este endpoint es publico y el
            // mensaje de un SQLException suele llevar host, puerto y nombre del
            // servicio ("ORA-12541: TNS:no listener" y companeros). Quien tiene que
            // arreglarlo mira el log; a quien solo pregunta desde fuera le basta con
            // saber que la base de datos no responde.
            log.warn("Health check: la base de datos no responde", ex);
            return new HealthResponse.DatabaseStatus("DOWN", null, null, "La base de datos no responde");
        }
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", Arrays.asList(profiles));
    }
}
