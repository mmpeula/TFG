package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pone fecha a las tareas que propone la IA.
 *
 * El modelo no elige cuando: si se le dejara, las fechas saldrian arbitrarias o
 * clavadas todas el mismo dia, y por muy razonables que suenen no encajarian con
 * el calendario real del workspace. Aqui se reparten de forma determinista, para
 * que al aplicar la propuesta las tareas caigan en un sprint de verdad del plan en
 * lugar de amontonarse en «sin fecha».
 *
 * <p>Cada persona avanza por su ventana a su propio ritmo: si Ana tiene dos
 * tareas y Luis cinco, las de Ana no se aprietan solo porque las de Luis sean
 * mas. Dentro de cada persona se respeta el orden en que llegan (que suele seguir
 * la secuencia logica del trabajo: analisis, luego implementacion, luego
 * pruebas), asi que la ultima tarea de cada uno cae siempre en la fecha de
 * entrega y las anteriores se reparten hacia atras desde ahi.</p>
 */
@Component
public class AiTaskScheduler {

    /** Ventana que se usa si el workspace no tiene fecha de entrega: seis semanas. */
    private static final int DEFAULT_WINDOW_DAYS = 42;

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

    public List<ProposedTask> schedule(List<ProposedTask> tasks, LocalDate startDate, LocalDate deadline) {
        if (tasks.isEmpty()) {
            return tasks;
        }

        Window window = resolveWindow(startDate, deadline);
        LocalDate start = window.start();
        LocalDate end = window.end();
        long spanDays = Math.max(1, ChronoUnit.DAYS.between(start, end));

        // Cada persona (o "sin asignar", agrupadas con la clave null) es una ventana propia.
        Map<Long, List<Integer>> porPersona = new LinkedHashMap<>();
        for (int index = 0; index < tasks.size(); index++) {
            porPersona.computeIfAbsent(tasks.get(index).assigneeId(), key -> new ArrayList<>()).add(index);
        }

        LocalDate[] fechas = new LocalDate[tasks.size()];
        for (List<Integer> indices : porPersona.values()) {
            int total = indices.size();
            for (int posicion = 0; posicion < total; posicion++) {
                long offset = Math.round(spanDays * (posicion + 1.0) / total);
                fechas[indices.get(posicion)] = start.plusDays(offset);
            }
        }

        List<ProposedTask> result = new ArrayList<>(tasks.size());
        for (int index = 0; index < tasks.size(); index++) {
            result.add(tasks.get(index).withDueDate(fechas[index]));
        }
        return result;
    }
}
