package com.tfg.kanban.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Solo cuenta una fecha pegada a una palabra que marque claramente el comienzo
 * del proyecto: una fecha suelta en cualquier otra parte del documento (un
 * ejemplo, un plazo de entrega) no dice nada de cuando empieza la practica.
 */
class DocumentDateDetectorTest {

    @Test
    @DisplayName("Encuentra una fecha en formato dd/mm/aaaa junto a \"fecha de inicio\"")
    void detectsSlashDate() {
        assertThat(DocumentDateDetector.detect("Datos del proyecto. Fecha de inicio: 05/10/2026. Duracion: 3 meses."))
                .contains(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("Encuentra una fecha en formato ISO")
    void detectsIsoDate() {
        assertThat(DocumentDateDetector.detect("El proyecto comienza el 2026-10-05 y dura tres meses."))
                .contains(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("Encuentra una fecha larga en espanol")
    void detectsLongSpanishDate() {
        assertThat(DocumentDateDetector.detect("La practica empieza el 5 de octubre de 2026."))
                .contains(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("Una fecha sin palabra clave cerca no cuenta, aunque este en el documento")
    void ignoresDatesWithoutAKeyword() {
        assertThat(DocumentDateDetector.detect("La entrega final es el 05/10/2026. Buena suerte."))
                .isEmpty();
    }

    @Test
    @DisplayName("Sin ninguna fecha, no encuentra nada")
    void emptyWhenNoDateAtAll() {
        assertThat(DocumentDateDetector.detect("Practica de bases de datos: modelo E/R y consultas SQL."))
                .isEmpty();
    }

    @Test
    @DisplayName("La encuentra cuando el guion la pone como cabecera de tabla (\"Comienzo de la practica\"), no como frase")
    void detectsDateAfterATableHeaderStylePhrase() {
        // Asi extrae PDFBox la tabla de un guion de practica real: la cabecera y el
        // dato de al lado quedan en lineas seguidas, no en la misma frase.
        String texto = "1. Temporizacion\nComienzo de la practica Duracion\n"
                + "Semana del 13 de octubre de 2025 16 de diciembre de 2025\n2. Descripcion...";

        assertThat(DocumentDateDetector.detect(texto)).contains(LocalDate.of(2025, 10, 13));
    }

    @Test
    @DisplayName("La encuentra dentro de una tabla HTML, en la celda siguiente a la etiqueta")
    void detectsDateInsideAnHtmlTableCell() {
        String html = "<table><tr><td>Fecha de inicio</td><td>05/10/2026</td></tr>"
                + "<tr><td>Duracion</td><td>3 meses</td></tr></table>";

        assertThat(DocumentDateDetector.detect(html)).contains(LocalDate.of(2026, 10, 5));
    }
}
