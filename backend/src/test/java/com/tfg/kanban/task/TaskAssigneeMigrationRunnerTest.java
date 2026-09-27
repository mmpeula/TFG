package com.tfg.kanban.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La migracion de {@code ASSIGNEE_ID} solo debe tocar la base de datos cuando
 * esa columna vieja existe de verdad: en una base nueva (Testcontainers, o una
 * instalacion desde cero) {@code ddl-auto=update} nunca la crea, y consultarla
 * a ciegas revienta con ORA-00904.
 */
class TaskAssigneeMigrationRunnerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TaskAssigneeMigrationRunner runner = new TaskAssigneeMigrationRunner(jdbcTemplate);
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @Test
    void noHaceNadaSiLaColumnaViejaNoExiste() {
        when(jdbcTemplate.queryForObject(contains("USER_TAB_COLUMNS"), eq(Integer.class))).thenReturn(0);

        runner.run(args);

        verify(jdbcTemplate, never()).queryForObject(contains("SELECT COUNT(*) FROM TASKS"), any(Class.class));
        verify(jdbcTemplate, never()).update(any(String.class));
    }

    @Test
    void noHaceNadaSiLaColumnaExistePeroNoHayPendientes() {
        when(jdbcTemplate.queryForObject(contains("USER_TAB_COLUMNS"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM TASKS"), eq(Integer.class))).thenReturn(0);

        runner.run(args);

        verify(jdbcTemplate, never()).update(any(String.class));
    }

    @Test
    void migraLasFilasPendientesYVaciaLaColumnaVieja() {
        when(jdbcTemplate.queryForObject(contains("USER_TAB_COLUMNS"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM TASKS"), eq(Integer.class))).thenReturn(3);
        when(jdbcTemplate.update(contains("INSERT INTO TASK_ASSIGNEES"))).thenReturn(3);

        runner.run(args);

        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO TASK_ASSIGNEES"));
        verify(jdbcTemplate, times(1)).update(contains("UPDATE TASKS SET ASSIGNEE_ID = NULL"));
    }
}
