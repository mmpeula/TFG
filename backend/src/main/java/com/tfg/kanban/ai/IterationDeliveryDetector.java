package com.tfg.kanban.ai;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca fechas de entrega por iteracion/sprint en el documento, para avisar de
 * ellas aunque no se usen para planificar.
 *
 * <p>Un guion de practica en PDF suele traer estas fechas en una tabla con una
 * columna ancha (la descripcion de la entrega, que ocupa varias lineas) y otra
 * estrecha (la fecha). Delimitar esa tabla por su area exigiria coordenadas
 * fijas, y el area cambia de un documento a otro; en su lugar, PDFBox con
 * {@code setSortByPosition} ordena por posicion vertical, lo que mezcla las
 * lineas cortas de la fecha con las de la descripcion larga de al lado. El texto
 * real que sale de un documento asi, para "1º Iteracion: 5-6 de noviembre", es
 * literalmente:</p>
 *
 * <pre>Informe de las reuniones de revision y
 * 1º Iteracion: 5-6 de
 * retrospectiva.
 * noviembre.</pre>
 *
 * <p>Una regex normal no encuentra "noviembre" pegado a "5-6 de" porque no lo
 * esta: "retrospectiva." se ha colado en medio. Por eso aqui no se busca un
 * patron continuo, sino el rango de dias y el nombre del mes por separado,
 * dentro de una ventana amplia tras "Iteracion N", sea cual sea lo que haya
 * entre medias.</p>
 */
final class IterationDeliveryDetector {

    /** Cuanto texto se mira tras "Iteracion N" para encontrar dias y mes, aunque esten separados. */
    private static final int WINDOW_CHARS = 150;

    private static final Pattern ITERATION = Pattern.compile("(?i)(\\d+)\\s*[ºª°]?\\s*iteraci[oó]n\\s*:?");
    private static final Pattern DAY_RANGE = Pattern.compile("\\b(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})\\s+de\\b");
    private static final Pattern SINGLE_DAY = Pattern.compile("\\b(\\d{1,2})\\s+de\\b");
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("(?i)curso\\s+(\\d{4})\\s*/\\s*(\\d{4})");
    private static final Pattern NOT_DEFINITIVE = Pattern.compile(
            "(?i)se concretar[aá]|se confirmar[aá]|pendiente de confirmar|fecha exacta");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    /** Una entrega de iteracion: un dia o un rango, con o sin fecha exacta confirmada. */
    record Delivery(int iteration, int day1, int day2, int month, int year, boolean definitive) {

        String describe() {
            String mes = Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "ES"));
            String cuando = day1 == day2
                    ? "el %d de %s de %d".formatted(day1, mes, year)
                    : "la semana del %d al %d de %s de %d".formatted(day1, day2, mes, year);
            String nota = definitive ? "" : " (el documento dice que la fecha exacta se confirmará aparte)";
            return "El documento indica que la entrega de la iteración %d es %s%s."
                    .formatted(iteration, cuando, nota);
        }
    }

    private IterationDeliveryDetector() {
    }

    static List<Delivery> detect(String text) {
        boolean definitive = !NOT_DEFINITIVE.matcher(text).find();
        List<Delivery> entregas = new ArrayList<>();

        Matcher iteracion = ITERATION.matcher(text);
        while (iteracion.find()) {
            int numero;
            try {
                numero = Integer.parseInt(iteracion.group(1));
            } catch (NumberFormatException ex) {
                continue;
            }
            String ventana = text.substring(iteracion.end(), Math.min(text.length(), iteracion.end() + WINDOW_CHARS));
            dayRangeIn(ventana).ifPresent(dias -> monthIn(ventana).ifPresent(mes ->
                    yearFor(mes, text).ifPresent(anio -> {
                        // Los dos dias tienen que ser fechas de verdad: si el rango o el
                        // mes casan por casualidad con numeros sueltos, mejor no avisar
                        // de una entrega que no existe.
                        if (esFechaValida(anio, mes, dias[0]) && esFechaValida(anio, mes, dias[1])) {
                            entregas.add(new Delivery(numero, dias[0], dias[1], mes, anio, definitive));
                        }
                    })));
        }
        return entregas;
    }

    /** El primer rango "D-D de" de la ventana, o un solo dia "D de" si no hay rango. */
    private static Optional<int[]> dayRangeIn(String ventana) {
        Matcher rango = DAY_RANGE.matcher(ventana);
        if (rango.find()) {
            try {
                return Optional.of(new int[] {Integer.parseInt(rango.group(1)), Integer.parseInt(rango.group(2))});
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        Matcher dia = SINGLE_DAY.matcher(ventana);
        if (dia.find()) {
            try {
                int d = Integer.parseInt(dia.group(1));
                return Optional.of(new int[] {d, d});
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** El primer nombre de mes que aparece en la ventana, este donde este. */
    private static Optional<Integer> monthIn(String ventana) {
        String plano = ventana.toLowerCase(Locale.ROOT);
        int mejor = -1;
        Integer mes = null;
        for (Map.Entry<String, Integer> entry : MESES.entrySet()) {
            int pos = plano.indexOf(entry.getKey());
            if (pos != -1 && (mejor == -1 || pos < mejor)) {
                mejor = pos;
                mes = entry.getValue();
            }
        }
        return Optional.ofNullable(mes);
    }

    /**
     * El curso academico ("Curso 2025/2026") dice que anio le toca a cada mes:
     * de agosto a diciembre el primero, de enero a julio el segundo. Sin ese
     * dato, se intenta con la fecha de inicio ya detectada; si tampoco hay,
     * el anio en curso.
     */
    private static boolean esFechaValida(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    private static Optional<Integer> yearFor(int month, String text) {
        Matcher curso = ACADEMIC_YEAR.matcher(text);
        if (curso.find()) {
            try {
                int primero = Integer.parseInt(curso.group(1));
                int segundo = Integer.parseInt(curso.group(2));
                return Optional.of(month >= 8 ? primero : segundo);
            } catch (NumberFormatException ex) {
                // sigue con los siguientes respaldos
            }
        }
        Optional<LocalDate> inicio = DocumentDateDetector.detect(text);
        if (inicio.isPresent()) return Optional.of(inicio.get().getYear());
        return Optional.of(LocalDate.now().getYear());
    }
}
