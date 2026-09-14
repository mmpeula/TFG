package com.tfg.kanban.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracion del modulo de IA. PlanifIA habla con un modelo que corre en
 * local (Ollama): no se envia nada a ningun servicio externo.
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

    /** Cuantas tareas como maximo se le piden al modelo. */
    private int maxTasks = 8;

    /** Temperatura baja: se busca consistencia, no creatividad. */
    private double temperature = 0.2;
}
