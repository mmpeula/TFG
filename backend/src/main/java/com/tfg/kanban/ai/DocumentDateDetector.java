package com.tfg.kanban.ai;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca en el texto del documento una fecha de inicio, para no tener que
 * preguntarla siempre a mano.
 *
 * <p>Solo cuenta una fecha pegada a una palabra que la marque claramente como el
 * comienzo del proyecto ("fecha de inicio", "comienza el"...). Una fecha suelta en
 * cualquier otra parte del documento —un ejemplo, un plazo de entrega, la fecha de
 * una reunion— no dice nada de cuando empieza la practica, y usarla igual daria
 * mas fechas mal adivinadas que aciertos.</p>
 */
final class DocumentDateDetector {

    /** Cuanto texto se mira despues de la palabra clave para encontrar la fecha. */
    private static final int WINDOW_CHARS = 60;

    private static final Pattern KEYWORD = Pattern.compile(
            "(?i)fecha de (inicio|comienzo)|(comienza|empieza|arranca|inicia)\\s+el|inicio del proyecto|kick[- ]?off"
            // Los guiones de practica suelen ponerlo como cabecera de una tabla
            // ("Comienzo de la practica"/"Inicio del proyecto"), no como frase.
            + "|comienzo de (la pr[aá]ctica|el proyecto|la asignatura)|inicio de (la pr[aá]ctica|la asignatura)");

    private static final Pattern SLASH_DATE = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern LONG_DATE = Pattern.compile(
            "(?i)(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private DocumentDateDetector() {
    }

    static Optional<LocalDate> detect(String text) {
        Matcher keyword = KEYWORD.matcher(text);
        while (keyword.find()) {
            int desde = keyword.end();
            String ventana = text.substring(desde, Math.min(text.length(), desde + WINDOW_CHARS));
            Optional<LocalDate> fecha = dateIn(ventana);
            if (fecha.isPresent()) return fecha;
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> dateIn(String ventana) {
        Matcher iso = ISO_DATE.matcher(ventana);
        if (iso.find()) {
            Optional<LocalDate> fecha = of(iso.group(1), iso.group(2), iso.group(3));
            if (fecha.isPresent()) return fecha;
        }
        Matcher slash = SLASH_DATE.matcher(ventana);
        if (slash.find()) {
            // dd/mm/aaaa, como se escriben las fechas en espanol.
            Optional<LocalDate> fecha = of(slash.group(3), slash.group(2), slash.group(1));
            if (fecha.isPresent()) return fecha;
        }
        Matcher largo = LONG_DATE.matcher(ventana);
        if (largo.find()) {
            Integer mes = MESES.get(largo.group(2).toLowerCase(Locale.ROOT));
            if (mes != null) {
                Optional<LocalDate> fecha = of(largo.group(3), String.valueOf(mes), largo.group(1));
                if (fecha.isPresent()) return fecha;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> of(String year, String month, String day) {
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)));
        } catch (NumberFormatException | DateTimeException ex) {
            return Optional.empty();
        }
    }
}
