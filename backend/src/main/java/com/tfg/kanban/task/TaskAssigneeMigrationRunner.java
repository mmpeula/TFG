package com.tfg.kanban.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migra, una sola vez, las tareas que tenian un unico responsable (columna
 * vieja {@code TASKS.ASSIGNEE_ID}) al modelo nuevo de varios responsables
 * (tabla {@code TASK_ASSIGNEES}). Con {@code ddl-auto=update} Hibernate crea
 * la tabla nueva sola, pero no migra datos ni borra la columna vieja: eso es
 * cosa de este runner, igual que {@link com.tfg.kanban.user.AdminBootstrapRunner}
 * deja lista la cuenta de administrador al arrancar.
 *
 * <p>Tras copiar cada fila, se vacia su {@code ASSIGNEE_ID} en la misma
 * pasada: la condicion de guarda ("quedan filas con ASSIGNEE_ID no nulo") se
 * vuelve asi monotona y nunca vuelve a cumplirse una vez migrado. Comprobar
 * en su lugar si {@code TASK_ASSIGNEES} esta vacia seria peligroso, porque un
 * usuario puede legitimamente dejar todas las tareas sin responsable con la
 * interfaz nueva: un reinicio del backend resucitaria entonces asignaciones
 * que se habian borrado a proposito.</p>
 *
 * <p>En una base de datos nueva (una instalacion desde cero, o el Oracle de
 * Testcontainers en los tests de integracion) {@code ddl-auto=update} nunca
 * llega a crear esa columna vieja, porque la entidad {@code Task} actual ya
 * no la declara: no hay nada que migrar. Por eso se comprueba primero, en el
 * diccionario de datos, que la columna existe antes de consultarla; si no
 * existe, el arranque no hace nada en vez de fallar con ORA-00904.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAssigneeMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) {
            return;
        }

        Integer pendientes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TASKS WHERE ASSIGNEE_ID IS NOT NULL", Integer.class);
        if (pendientes == null || pendientes == 0) {
            return;
        }

        int copiadas = jdbcTemplate.update(
                "INSERT INTO TASK_ASSIGNEES (TASK_ID, USER_ID) "
                        + "SELECT ID, ASSIGNEE_ID FROM TASKS WHERE ASSIGNEE_ID IS NOT NULL");
        jdbcTemplate.update("UPDATE TASKS SET ASSIGNEE_ID = NULL WHERE ASSIGNEE_ID IS NOT NULL");

        log.info("Migradas {} asignaciones de tareas al nuevo modelo multi-responsable", copiadas);
    }

    /** Si {@code TASKS.ASSIGNEE_ID} ya no existe, es una base nueva y no hay nada que migrar. */
    private boolean legacyColumnExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TASKS' AND COLUMN_NAME = 'ASSIGNEE_ID'",
                Integer.class);
        return count != null && count > 0;
    }
}
