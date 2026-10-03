package com.tfg.kanban.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracion del modulo de IA. PlanifIA habla con un modelo alojado en
 * DeepSeek; el documento y los datos del equipo que se mandan en el prompt
 * salen de esta maquina hacia sus servidores para generar el plan.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Si es false, el endpoint responde 503 explicando que la IA esta apagada. */
    private boolean enabled = true;

    /** URL de la API de DeepSeek, compatible con el formato de OpenAI. */
    private String baseUrl = "https://api.deepseek.com";

    /** Clave de API de DeepSeek. Sin ella no se puede hablar con el modelo. */
    private String apiKey = "";

    /** Modelo a usar, de los que sirve DeepSeek (ver api-docs.deepseek.com). */
    private String model = "deepseek-flash";

    /**
     * El plan entero, con todos sus sprints, sale de una sola respuesta: con un
     * documento largo y un equipo grande son varios miles de tokens de salida.
     */
    private Duration timeout = Duration.ofMinutes(5);

    /**
     * Tope de seguridad de cuantas tareas se aceptan del plan completo: un corte
     * por si el documento tiene muchos sprints y la suma se dispara.
     */
    private int maxTasks = 60;

    /** Temperatura baja: se busca un reparto consistente, no creatividad. */
    private double temperature = 0.2;

    /**
     * Tope de tokens que el modelo puede generar en una sola respuesta
     * (max_tokens). Tiene que caber el plan completo de todos los sprints de una
     * vez; DeepSeek solo cobra lo que de verdad genera, asi que un techo alto no
     * cuesta nada si el plan es corto, y uno bajo corta el JSON a medias.
     */
    private int maxOutputTokens = 32_000;

    /**
     * Tope de tokens de una respuesta del asistente de chat. Mucho mas bajo que
     * {@link #maxOutputTokens}: el propio prompt le pide respuestas de 120
     * palabras salvo que hagan falta pasos o un fragmento de codigo, y sin techo
     * propio una respuesta que se desmadra pagaria (y esperaria) como si fuera
     * generando un plan entero.
     */
    private int chatMaxOutputTokens = 1_500;

    /** Como se procesa el documento antes de llegar al modelo (ver {@code ai.document}). */
    private final Document document = new Document();

    /**
     * Limites del procesamiento de documentos. Todo lo que se puede decidir en
     * local se decide aqui, con estos numeros: DeepSeek solo se usa para lo que
     * no se puede leer sin ver (paginas escaneadas, imagenes con informacion).
     */
    @Getter
    @Setter
    public static class Document {

        /** Si es false, un documento que necesite leerse visualmente se rechaza explicando por que. */
        private boolean visionEnabled = true;

        /** Paginas de un PDF que se leen como mucho; el resto se descarta avisando. */
        private int maxPages = 300;

        /** Tope del enunciado en texto, como red de seguridad ante un fichero descomunal. */
        private int maxTextChars = 80_000;

        /** Tope de todas las tablas juntas. Una tabla que no cabe se omite entera, no a medias. */
        private int maxTablesChars = 30_000;

        /** Tope de lo que devuelve el analisis visual. */
        private int maxVisualChars = 20_000;

        /** Una pagina con menos caracteres utiles que esto se considera sin texto (portada, escaneo...). */
        private int minPageChars = 40;

        /** Paginas que se mandan a leer visualmente como mucho, entre escaneadas y con tablas dudosas. */
        private int maxVisualPages = 20;

        /** Imagenes incrustadas que se mandan a analizar como mucho, las mas grandes primero. */
        private int maxImages = 6;

        /** Una imagen mas pequena que esto (px, por lado) es un icono o un adorno. */
        private int minImageSide = 120;

        /** Lado mayor al que se reduce cualquier imagen antes de enviarla. */
        private int maxImageSide = 1_568;

        /** Resolucion a la que se dibuja una pagina para leerla visualmente. */
        private int renderDpi = 150;

        /** Imagenes por peticion: menos llamadas, a costa de respuestas mas largas. */
        private int imagesPerRequest = 4;

        /** Tope de tokens de una respuesta del analisis visual. */
        private int visionMaxOutputTokens = 8_000;

        /** Documentos ya procesados que se recuerdan, para no pagar dos veces la vision al reintentar. */
        private int cacheEntries = 8;

        private Duration cacheTtl = Duration.ofHours(1);
    }
}
