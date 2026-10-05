package com.tfg.kanban.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Directorio donde se guardan las fotos de perfil y de los espacios (en Docker, un volumen). */
    private String uploadDir = "./uploads";

    /** Tamano maximo de una foto o de un documento subido, en bytes. Por defecto 10 MB. */
    private long maxFileSize = 10L * 1024 * 1024;
}
