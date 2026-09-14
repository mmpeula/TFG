package com.tfg.kanban.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Directorio donde se guardan los ficheros (en Docker, un volumen). */
    private String uploadDir = "./uploads";

    /** Tamano maximo por fichero, en bytes. Por defecto 10 MB. */
    private long maxFileSize = 10L * 1024 * 1024;

    /** Tipos MIME permitidos. */
    private List<String> allowedContentTypes = List.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml",
            "application/pdf", "text/plain", "text/markdown", "text/csv",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
}
