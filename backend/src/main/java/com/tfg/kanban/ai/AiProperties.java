package com.tfg.kanban.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracion del modulo de IA. PlanifIA habla con un modelo que corre en
 * local (Ollama): no se envia nada a ningun servicio externo. El proveedor es
 * siempre Ollama; lo configurable es que modelo se usa y como se le llama.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Si es false, el endpoint responde 503 explicando que la IA esta apagada. */
    private boolean enabled = true;

    /** URL del servidor de Ollama. */
    private String baseUrl = "http://localhost:11434";

    /** Modelo a usar. Debe estar descargado en el servidor (ollama pull ...). */
    private String model = "llama3";

    /** Un modelo de 8B en CPU tarda: el tiempo de espera es generoso a proposito. */
    private Duration timeout = Duration.ofMinutes(3);

    /**
     * Tope de seguridad de tareas que se aceptan de una respuesta. No es cuantas
     * se le piden: un backlog de varios sprints y personas necesita decenas, y
     * esto solo corta una respuesta desbocada.
     */
    private int maxTasks = 60;

    /** Temperatura baja: se busca un plan consistente, no creatividad. */
    private double temperature = 0.3;

    /**
     * Ventana de contexto que se pide a Ollama (num_ctx). Su valor por defecto,
     * 2048, no da ni para el enunciado: el prompt de sistema, el documento, el
     * equipo y un backlog completo en JSON necesitan bastante mas. Por debajo de
     * 8192 el JSON sale cortado a medias.
     *
     * <p>No se pone mas por defecto porque llama3 se entreno con 8192: por encima
     * de su contexto nativo la salida empeora en vez de mejorar. Con un modelo de
     * contexto largo (llama3.1, qwen2.5...) conviene subirlo con AI_NUM_CTX, sobre
     * todo si se corrigen planes, que reenvian el anterior.</p>
     */
    private int numCtx = 8192;

    /**
     * Cuantas veces se le devuelve al modelo un plan que no pasa la validacion,
     * con los errores concretos, para que lo corrija. Despues se muestra el
     * ultimo tal cual con esos errores como avisos.
     */
    private int maxRetries = 2;
}
