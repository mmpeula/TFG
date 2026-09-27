package com.tfg.kanban.ai;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Localiza, con una heuristica generica (no atada a Scrum, a JADE ni a ninguna
 * metodologia o asignatura concreta), donde empieza la parte de un documento
 * que reparte nota, marca algo como obligatorio o dice que hay que entregar:
 * un titulo como "Evaluacion", "Rubrica", "Criterios", "Puntuacion",
 * "Requisitos" o "Entrega".
 *
 * <p>Existe por esto: el documento se recorta antes de mandarlo al modelo
 * (ver {@code AiPlanService.DOCUMENT_CHARS_FOR_MODEL}, necesario para no
 * pasarse de la cuota de tokens del proveedor). Si esa seccion cae justo
 * despues del recorte —algo habitual, porque la evaluacion suele ir al final
 * del documento—, el modelo nunca llega a verla y reparte tareas sin saber
 * que hay apartados puntuables que cubrir. Lo que devuelve esta clase se
 * manda aparte, siempre, este o no dentro de la parte que sobrevive al
 * recorte.</p>
 */
final class EvaluationSectionDetector {

    private static final Pattern[] KEYWORDS = {
            Pattern.compile("evalua"), Pattern.compile("rubrica"), Pattern.compile("criterio"),
            Pattern.compile("puntuacion"), Pattern.compile("requisito"), Pattern.compile("entrega"),
    };

    /**
     * De sobra para una rubrica con su lista de entregables detras (el caso
     * real mas habitual: van juntas, al final del documento), sin acercarse a
     * lo que le cuesta en tokens repetir el documento entero.
     */
    private static final int MAX_SECTION_CHARS = 2_500;

    /** Por encima de esto ya no es un titulo, es un parrafo que solo menciona la palabra de pasada. */
    private static final int MAX_HEADING_LENGTH = 100;

    /** "4 Evaluacion...", "4.1 Rubrica...", "IV. Criterios...": numero, punto opcional, texto. */
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\d+(\\.\\d+)*\\.?\\s+\\S.*");

    private EvaluationSectionDetector() {
    }

    /**
     * El fragmento del documento, tal cual esta escrito (sin resumirlo con el
     * modelo: eso costaria otra llamada solo para esto), desde el primer titulo
     * que parece repartir nota o marcar un requisito. Vacio si no se reconoce
     * ninguno.
     */
    static Optional<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] lines = text.split("\n", -1);

        int inicio = indexOfFirst(lines, EvaluationSectionDetector::esEncabezadoFuerte);
        if (inicio < 0) {
            inicio = indexOfFirst(lines, EvaluationSectionDetector::esEncabezadoDebil);
        }
        if (inicio < 0) {
            return Optional.empty();
        }

        StringBuilder seccion = new StringBuilder();
        for (int i = inicio; i < lines.length && seccion.length() < MAX_SECTION_CHARS; i++) {
            seccion.append(lines[i]).append('\n');
        }
        // El bucle solo corta entre lineas: si una linea sola ya se pasa del tope
        // (nada que ver en un documento normal, pero mejor no confiar en eso), se
        // recorta aqui de verdad.
        String extracto = seccion.length() > MAX_SECTION_CHARS
                ? seccion.substring(0, MAX_SECTION_CHARS).strip() : seccion.toString().strip();
        return extracto.isBlank() ? Optional.empty() : Optional.of(extracto);
    }

    private static int indexOfFirst(String[] lines, java.util.function.Predicate<String> condicion) {
        for (int i = 0; i < lines.length; i++) {
            if (condicion.test(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Un titulo numerado o en mayusculas que menciona alguna palabra clave: el
     * indicio mas fiable de que es de verdad un titulo, no una frase de en medio
     * de un parrafo que solo cita la palabra de pasada.
     */
    private static boolean esEncabezadoFuerte(String line) {
        String trimmed = line.strip();
        if (!candidatoAEncabezado(trimmed)) {
            return false;
        }
        boolean formaDeTitulo = NUMBERED_HEADING.matcher(trimmed).matches()
                || (trimmed.equals(trimmed.toUpperCase(java.util.Locale.ROOT)) && trimmed.chars().anyMatch(Character::isLetter));
        return formaDeTitulo && contieneAlgunaClave(trimmed);
    }

    /**
     * Sin forma de titulo, pero al menos corta y con mayuscula o numero al
     * principio: mejor esto que nada si el documento no numera sus apartados.
     */
    private static boolean esEncabezadoDebil(String line) {
        String trimmed = line.strip();
        if (!candidatoAEncabezado(trimmed)) {
            return false;
        }
        char primera = trimmed.charAt(0);
        return (Character.isUpperCase(primera) || Character.isDigit(primera)) && contieneAlgunaClave(trimmed);
    }

    private static boolean candidatoAEncabezado(String trimmed) {
        return !trimmed.isEmpty() && trimmed.length() <= MAX_HEADING_LENGTH;
    }

    private static boolean contieneAlgunaClave(String trimmed) {
        String normalizado = TextNormalizer.plain(trimmed);
        for (Pattern keyword : KEYWORDS) {
            if (keyword.matcher(normalizado).find()) {
                return true;
            }
        }
        return false;
    }
}
