package com.tfg.kanban.ai;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Deja un texto del modelo en una forma comparable.
 *
 * <p>Hace falta en dos sitios que comparan cosas escritas por un modelo pequeno:
 * los nombres de las personas a las que dice asignar una tarea
 * ({@link WorkloadBalancer}) y los titulos de las tareas que propone
 * ({@link AiPlanService}, para no repetir lo que ya esta en el tablero). En los
 * dos casos el problema es el mismo —«Ana García» y «ana garcia» son la misma
 * persona— y estaba resuelto dos veces por separado, asi que bastaba con tocar
 * una para que dejaran de coincidir.</p>
 */
final class TextNormalizer {

    /**
     * Palabras que no distinguen un titulo de otro. Se quitan para que «Escribir
     * la memoria» y «Escribir memoria» cuenten como la misma tarea.
     */
    private static final List<String> STOP_WORDS = List.of("del", "las", "los", "una", "para", "con", "que");

    /** Por debajo de esto una palabra es un articulo o una preposicion, no contenido. */
    private static final int MIN_WORD_LENGTH = 3;

    private TextNormalizer() {
    }

    /** Sin tildes, en minusculas, sin signos y con los espacios de sobra quitados. */
    static String plain(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Como {@link #plain}, pero ademas sin las palabras que no dicen nada. */
    static String keywords(String value) {
        return Arrays.stream(plain(value).split(" "))
                .filter(word -> word.length() >= MIN_WORD_LENGTH)
                .filter(word -> !STOP_WORDS.contains(word))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
