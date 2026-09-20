package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pone fecha a las tareas que propone la IA.
 *
 * <p>El modelo no elige cuando: si se le dejara, las fechas saldrian arbitrarias o
 * clavadas todas el mismo dia. Aqui se reparten de forma determinista y, sobre
 * todo, <b>por sprints</b>: la ventana de trabajo se parte en iteraciones con la
 * misma regla que usa la pantalla de Plan (ver {@code lib/sprints.ts}) y cada
 * tarea cae al final de la suya. Asi lo que se aplica al tablero encaja con los
 * sprints que el usuario ve, en vez de en fechas sueltas entre medias.</p>
 *
 * <p>Cada persona recorre todos los sprints a su ritmo: su primera tarea entra en
 * el primero y la ultima en el ultimo, repartiendo el resto por el medio. Es la
 * diferencia con repartir por fecha a secas, que amontonaba el trabajo de todos
 * contra la entrega y dejaba vacios los primeros sprints. Dentro de cada persona
 * se respeta el orden en que llegan las tareas, que suele seguir la secuencia
 * logica del trabajo: analisis, luego implementacion, luego pruebas.</p>
 */
@Component
public class AiTaskScheduler {

    /** Ventana que se usa si el workspace no tiene fecha de entrega: seis semanas. */
    private static final int DEFAULT_WINDOW_DAYS = 42;

    /** La misma que asume la pantalla de Plan cuando el workspace no lo dice. */
    static final int DEFAULT_SPRINT_DAYS = 14;

    /** Ventana de trabajo resuelta: ni empieza en el pasado ni acaba antes de empezar. */
    public record Window(LocalDate start, LocalDate end) {
    }

    /** Resuelve la ventana de fechas dentro de la que se van a repartir las tareas. */
    Window resolveWindow(LocalDate startDate, LocalDate deadline) {
        LocalDate today = LocalDate.now();
        LocalDate start = startDate != null && startDate.isAfter(today) ? startDate : today;
        LocalDate end = deadline != null && deadline.isAfter(start) ? deadline : start.plusDays(DEFAULT_WINDOW_DAYS);
        return new Window(start, end);
    }

    /**
     * Parte la ventana en sprints.
     *
     * <p>Copia deliberada de {@code buildPlan} en {@code lib/sprints.ts}: si las dos
     * reglas no coincidieran, las tareas caerian entre dos sprints de los que se
     * pintan y el plan volveria a salir con huecos. Incluye por eso sus dos
     * detalles: un ultimo trozo demasiado corto se suma al anterior en vez de ser
     * un sprint de dos dias, y el ultimo sprint llega siempre hasta el final de la
     * ventana.</p>
     */
    List<Window> sprintsOf(Window window, Integer sprintDays) {
        int dias = sprintDays == null || sprintDays < 1 ? DEFAULT_SPRINT_DAYS : sprintDays;
        long total = ChronoUnit.DAYS.between(window.start(), window.end()) + 1;

        int cuantos = (int) Math.max(1, Math.ceil((double) total / dias));
        long sobra = total - (long) (cuantos - 1) * dias;
        if (cuantos > 1 && sobra <= dias / 2.0) {
            cuantos--;
        }

        List<Window> sprints = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            LocalDate inicio = window.start().plusDays((long) i * dias);
            LocalDate fin = i == cuantos - 1 ? window.end() : inicio.plusDays(dias - 1L);
            sprints.add(new Window(inicio, fin));
        }
        return sprints;
    }

    public List<ProposedTask> schedule(List<ProposedTask> tasks, LocalDate startDate, LocalDate deadline,
                                       Integer sprintDays) {
        if (tasks.isEmpty()) {
            return tasks;
        }

        List<Window> sprints = sprintsOf(resolveWindow(startDate, deadline), sprintDays);
        int ultimoSprint = sprints.size() - 1;

        // Cada persona (o "sin asignar", agrupadas con la clave null) recorre los
        // sprints por su cuenta: que una tenga cinco tareas y otra dos no cambia
        // cuando entrega la segunda.
        Map<Long, List<Integer>> porPersona = new LinkedHashMap<>();
        for (int index = 0; index < tasks.size(); index++) {
            porPersona.computeIfAbsent(tasks.get(index).assigneeId(), key -> new ArrayList<>()).add(index);
        }

        LocalDate[] fechas = new LocalDate[tasks.size()];
        for (List<Integer> indices : porPersona.values()) {
            int suyas = indices.size();
            for (int posicion = 0; posicion < suyas; posicion++) {
                // Con una sola tarea, al ultimo sprint: es su entregable, no algo
                // que despachar el primer dia. Con varias, de la primera iteracion
                // a la ultima repartidas por igual.
                int sprint = suyas == 1
                        ? ultimoSprint
                        : (int) Math.round((double) posicion * ultimoSprint / (suyas - 1));
                fechas[indices.get(posicion)] = sprints.get(sprint).end();
            }
        }

        List<ProposedTask> result = new ArrayList<>(tasks.size());
        for (int index = 0; index < tasks.size(); index++) {
            result.add(tasks.get(index).withDueDate(fechas[index]));
        }
        return result;
    }
}
