package com.tfg.kanban.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La heuristica se prueba con el texto real de un guion de practica (JADE,
 * agentes en un mundo bidimensional) que tiene su rubrica al final del
 * documento —el caso mas comun y justo el que el recorte por longitud se
 * dejaria fuera— para comprobar que localiza el titulo de verdad y no una
 * mencion suelta de la palabra en mitad de un parrafo.
 */
class EvaluationSectionDetectorTest {

    /** Texto real extraido de un guion de practica de agentes con JADE, con su rubrica de evaluacion al final. */
    private static final String DOCUMENTO_JADE = """
            Práctica 2. Movimiento de una
            agente en un mundo
            bidimensional
            DISEÑO BASADO EN AGENTES CURSO 2025/2026

            MANUEL JESÚS COBO MARTÍN
            DEPARTAMENTO DE CIENCIAS DE LA COMUTACIÓN E INTELIGENCIA ARTIFICIAL |
            UNIVERSIDAD DE GRANADA

            1 Contexto
            En la práctica anterior hemos visto el funcionamiento global de JADE, desde cómo lanzar el
            servidor hasta cómo crear un agente, añadirle diferentes tipos de comportamientos y ejecutar el
            agente en el servidor.
            La práctica se realizará en grupos de 4 personas, salvo excepciones debidamente justificadas.

            2 Descripción de la práctica
            En esta práctica se desarrollará un agente autónomo capaz de desplazarse por un mundo
            bidimensional evitando obstáculos y gestionando su energía.
            No se proporcionará ninguna arquitectura base: el diseño será completamente responsabilidad del
            estudiante. La evaluación valorará no solo el correcto funcionamiento del sistema, sino también
            la coherencia estructural del modelo, el respeto a los principios de encapsulación y la claridad de
            la separación de responsabilidades.
            La implementación de estos componentes y su correcta interacción constituirán un criterio de
            evaluación esencial de la práctica.

            3 Ejercicios a resolver
            En particular, el agente deberá, como mínimo, poder resolver los siguientes mundos o mapas:
            Sin obstáculos, yendo directo al objetivo.
            Con obstáculos básicos. Una línea horizontal de obstáculos, así como una línea vertical
            Con obstáculos cóncavos. El agente tendrá que ser capaz de enfrentarse a obstáculos en
            forma de U invertida.
            Con obstáculos convexos. El agente tendrá que ser capaz de enfrentarse a obstáculos en
            forma de U.

            4 Evaluación de la práctica
            La práctica se evaluará sobre 10 puntos, considerando los siguientes apartados:
            Correcta resolución de los mundos. Se tendrá en cuenta la energía consumida y se
            comparará con la obtenida por el resto de grupos. (NOTA se penalizará agentes que
            consuman demasiada energía en llegar a su objetivo)
            o Mundo sin obstáculos y mundo con obstáculos básicos (0.5 puntos).
            o Mundo con obstáculos cóncavos (0.5 puntos).
            o Mundo con obstáculos convexos (1 punto).
            o Mundos complejos (2)
            o Correcta resolución de un mundo sorpresa (2 puntos).
            Código y justificación de la arquitectura de software. Se valorará la separación de las
            clases, el respeto del principio de responsabilidad única, así como la legibilidad del
            código (3 puntos).
            Presentación. En clase de teoría se realizará una presentación de 5 minutos explicando
            las decisiones de diseño e implementación que se han tomado, así como los errores
            cometidos y cómo se han resuelto los mismos. La presentación se evaluará de forma
            colectiva (1 puntos).

            5 Documentos a entregar
            Para la evaluación de la práctica se deberá entregar el código fuente, una memoria explicando la
            estructura de clases, así como las decisiones de diseño e implementación tomadas, y la
            presentación o video que se usará el día de la presentación.
            """;

    @Test
    @DisplayName("Encuentra la seccion de evaluacion aunque vaya al final del documento, con todos sus apartados puntuables")
    void detectsTheEvaluationSectionAtTheEndOfTheDocument() {
        Optional<String> seccion = EvaluationSectionDetector.detect(DOCUMENTO_JADE);

        assertThat(seccion).isPresent();
        assertThat(seccion.get())
                .startsWith("4 Evaluación de la práctica")
                .contains("(0.5 puntos)")
                .contains("(1 punto)")
                .contains("Mundos complejos (2)")
                .contains("(2 puntos)")
                .contains("(3 puntos)")
                .contains("(1 puntos)")
                // La seccion de entrega, justo despues, se queda dentro del mismo extracto.
                .contains("Documentos a entregar");
    }

    @Test
    @DisplayName("No confunde una mencion suelta de \"evaluacion\" en mitad de un parrafo con el titulo de verdad")
    void doesNotStartAtALooseMentionWhenARealHeadingExistsLater() {
        Optional<String> seccion = EvaluationSectionDetector.detect(DOCUMENTO_JADE);

        // La primera vez que aparece la palabra es en una frase de en medio del
        // apartado 2 ("La evaluación valorará..."), mucho antes del titulo real.
        assertThat(seccion).isPresent();
        assertThat(seccion.get()).doesNotStartWith("La evaluación valorará");
    }

    @Test
    @DisplayName("Sin ninguna palabra clave, no encuentra nada")
    void returnsEmptyWhenThereIsNoEvaluationSection() {
        String documento = """
                1 Introducción
                Este documento describe el desarrollo de una aplicacion web sencilla.

                2 Funcionalidades
                La aplicacion debe permitir crear, editar y borrar tareas.
                """;

        assertThat(EvaluationSectionDetector.detect(documento)).isEmpty();
    }

    @Test
    @DisplayName("Un documento vacio o nulo no encuentra nada")
    void returnsEmptyForBlankOrNullInput() {
        assertThat(EvaluationSectionDetector.detect("")).isEmpty();
        assertThat(EvaluationSectionDetector.detect("   ")).isEmpty();
        assertThat(EvaluationSectionDetector.detect(null)).isEmpty();
    }

    @Test
    @DisplayName("El extracto tiene un tope de tamano: no repite el documento entero si la seccion es larguisima")
    void capsTheExtractedSectionLength() {
        String relleno = "Este parrafo describe con mucho detalle un apartado del contrato. ".repeat(200);
        String documento = "5 Requisitos\n" + relleno;

        Optional<String> seccion = EvaluationSectionDetector.detect(documento);

        assertThat(seccion).isPresent();
        assertThat(seccion.get().length()).isLessThan(documento.length());
        assertThat(seccion.get().length()).isLessThan(3_000);
    }

    @Test
    @DisplayName("Funciona igual para un encargo de cliente que habla de \"condiciones de aceptacion\", sin vocabulario de Scrum")
    void worksForAGenericClientRequestNotShapedAsAScrumDocument() {
        String documento = """
                Propuesta de desarrollo de tienda online

                El cliente solicita una tienda online basica con catalogo y carrito de compra.

                Requisitos obligatorios
                - El sistema debe soportar pagos con tarjeta.
                - Es obligatorio que el catalogo se pueda filtrar por categoria.
                - Se penalizara cualquier entrega que no incluya pruebas automatizadas.
                """;

        Optional<String> seccion = EvaluationSectionDetector.detect(documento);

        assertThat(seccion).isPresent();
        assertThat(seccion.get())
                .startsWith("Requisitos obligatorios")
                .contains("pagos con tarjeta")
                .contains("Se penalizara");
    }
}
