package com.tfg.kanban.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las fechas de entrega por iteracion se prueban con el texto tal y como sale
 * de verdad de PDFBox en un guion de practica con tablas, no con frases limpias
 * escritas a mano: es la unica forma de que el test sirva de algo.
 */
class IterationDeliveryDetectorTest {

    @Test
    @DisplayName("Encuentra un rango de dias y el mes aunque haya texto de otra columna colado en medio")
    void findsARangeEvenWithUnrelatedTextInTheMiddle() {
        // Esto es literalmente lo que extrae PDFBox de una tabla real con una
        // columna larga (la descripcion) y otra corta (la fecha): "retrospectiva."
        // pertenece a la columna de al lado y se cuela entre "de" y "noviembre".
        String texto = "Curso 2025/2026\n"
                + "Informe de las reuniones de revision y\n"
                + "1º Iteracion: 5-6 de\n"
                + "retrospectiva.\n"
                + "noviembre.\n"
                + "* Se concretara en Prado la fecha exacta para cada grupo";

        List<IterationDeliveryDetector.Delivery> entregas = IterationDeliveryDetector.detect(texto);

        assertThat(entregas).hasSize(1);
        IterationDeliveryDetector.Delivery entrega = entregas.get(0);
        assertThat(entrega.iteration()).isEqualTo(1);
        assertThat(entrega.day1()).isEqualTo(5);
        assertThat(entrega.day2()).isEqualTo(6);
        assertThat(entrega.month()).isEqualTo(11);
        assertThat(entrega.year()).isEqualTo(2025);
    }

    @Test
    @DisplayName("Un solo dia (sin rango) tambien se reconoce")
    void findsASingleDayWithoutARange() {
        String texto = "Curso 2025/2026\n3º Iteracion: 16 de\ndiciembre.";

        List<IterationDeliveryDetector.Delivery> entregas = IterationDeliveryDetector.detect(texto);

        assertThat(entregas).hasSize(1);
        assertThat(entregas.get(0).day1()).isEqualTo(16);
        assertThat(entregas.get(0).day2()).isEqualTo(16);
        assertThat(entregas.get(0).month()).isEqualTo(12);
    }

    @Test
    @DisplayName("Con \"se concretara\" en el documento, la entrega se marca como no definitiva")
    void marksAsNotDefinitiveWhenTheDocumentSaysSo() {
        String texto = "Curso 2025/2026\n1º Iteracion: 5-6 de noviembre.\n"
                + "* Se concretara en Prado la fecha exacta para cada grupo";

        var entrega = IterationDeliveryDetector.detect(texto).get(0);

        assertThat(entrega.definitive()).isFalse();
        assertThat(entrega.describe())
                .contains("iteración 1")
                .contains("5 al 6 de noviembre de 2025")
                .contains("se confirmará aparte");
    }

    @Test
    @DisplayName("Sin ningun aviso de que la fecha se confirmara despues, se marca como definitiva")
    void marksAsDefinitiveWithoutAnyDisclaimer() {
        String texto = "Curso 2025/2026\n1º Iteracion: 5-6 de noviembre.";

        var entrega = IterationDeliveryDetector.detect(texto).get(0);

        assertThat(entrega.definitive()).isTrue();
        assertThat(entrega.describe()).doesNotContain("confirmara");
    }

    @Test
    @DisplayName("El anio sale del curso academico, no de un mes cualquiera: agosto-diciembre es el primero")
    void infersTheYearFromTheAcademicCourse() {
        String texto = "Curso 2025/2026\n"
                + "1º Iteracion: 5-6 de noviembre.\n" // agosto-diciembre: primer anio (2025)
                + "2º Iteracion: 10 de marzo."; // enero-julio: segundo anio (2026)

        List<IterationDeliveryDetector.Delivery> entregas = IterationDeliveryDetector.detect(texto);

        assertThat(entregas).extracting(IterationDeliveryDetector.Delivery::iteration,
                        IterationDeliveryDetector.Delivery::year)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, 2025),
                        org.assertj.core.groups.Tuple.tuple(2, 2026));
    }

    @Test
    @DisplayName("Sin curso academico, el anio sale de la fecha de inicio ya detectada en el documento")
    void fallsBackToTheDetectedStartDateYear() {
        String texto = "Fecha de inicio: 13/10/2025\n1º Iteracion: 5-6 de noviembre.";

        var entrega = IterationDeliveryDetector.detect(texto).get(0);

        assertThat(entrega.year()).isEqualTo(2025);
    }

    @Test
    @DisplayName("Sin ninguna mencion a iteraciones, no se encuentra nada")
    void emptyWithoutAnyIterationMention() {
        assertThat(IterationDeliveryDetector.detect("Practica de bases de datos: modelo E/R y consultas SQL."))
                .isEmpty();
    }

    @Test
    @DisplayName("El PDF de ejemplo real: las tres iteraciones, con sus rangos y sin inventar ninguna fecha exacta")
    void matchesTheRealExampleDocument() {
        // Texto tal cual lo extrae PDFBox del PDF de ejemplo, con las dos tablas.
        String texto = "Curso 2025/2026\n"
                + "Comienzo de la practica Duracion\n"
                + "Semana del 13 de octubre de 2025 16 de diciembre de 2025\n"
                + "Informe de las reuniones de revision y\n"
                + "1º Iteracion: 5-6 de\n"
                + "retrospectiva.\n"
                + "noviembre.\n"
                + "2º Iteracion: 26-27 de\n"
                + "noviembre.\n"
                + "3º Iteracion: 16 de\n"
                + "diciembre.\n"
                + "* Se concretara en Prado (https://prado.ugr.es/) la fecha exacta para cada grupo";

        assertThat(DocumentDateDetector.detect(texto)).contains(LocalDate.of(2025, 10, 13));

        List<IterationDeliveryDetector.Delivery> entregas = IterationDeliveryDetector.detect(texto);
        assertThat(entregas).hasSize(3);
        assertThat(entregas).allSatisfy(entrega -> assertThat(entrega.definitive()).isFalse());
        assertThat(entregas.get(0).describe()).contains("5 al 6 de noviembre de 2025");
        assertThat(entregas.get(1).describe()).contains("26 al 27 de noviembre de 2025");
        assertThat(entregas.get(2).describe()).contains("el 16 de diciembre de 2025");
    }
}
