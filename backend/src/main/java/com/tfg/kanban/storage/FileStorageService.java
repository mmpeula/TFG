package com.tfg.kanban.storage;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda los adjuntos en el sistema de ficheros local (un volumen de Docker en
 * despliegue). No se usa S3 ni ningun servicio externo: el TFG no lo necesita.
 *
 * <p>Los ficheros se guardan con un nombre aleatorio, de forma que el nombre que
 * escribe el usuario nunca llega al sistema de ficheros y no hay riesgo de
 * <i>path traversal</i> ni de colisiones.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProperties properties;
    private Path root;

    @PostConstruct
    void init() {
        try {
            root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("Directorio de adjuntos: {}", root);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido crear el directorio de adjuntos", ex);
        }
    }

    /** Valida y guarda el fichero; devuelve el nombre con el que se ha almacenado. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("El fichero esta vacio");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessRuleException("El fichero supera el tamano maximo permitido (%d MB)"
                    .formatted(properties.getMaxFileSize() / (1024 * 1024)));
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            throw new BusinessRuleException("Tipo de fichero no permitido: " + contentType);
        }

        String storedName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        Path destination = root.resolve(storedName).normalize();
        if (!destination.getParent().equals(root)) {
            throw new BusinessRuleException("Ruta de destino invalida");
        }

        try (var input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido guardar el fichero", ex);
        }
        return storedName;
    }

    public Resource loadAsResource(String storedName) {
        try {
            Path file = root.resolve(storedName).normalize();
            if (!file.getParent().equals(root)) {
                throw new BusinessRuleException("Ruta de fichero invalida");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("El fichero ya no esta disponible en el servidor");
            }
            return resource;
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido leer el fichero", ex);
        }
    }

    public void delete(String storedName) {
        try {
            Path file = root.resolve(storedName).normalize();
            if (file.getParent().equals(root)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            log.warn("No se ha podido borrar el fichero {}: {}", storedName, ex.getMessage());
        }
    }

    /** Nombre original saneado, para usarlo en la cabecera de descarga. */
    public static String cleanFilename(String originalFilename) {
        String name = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(name)) {
            return "adjunto";
        }
        return name.replaceAll("[\\r\\n\"]", "").trim();
    }

    private String extensionOf(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        return StringUtils.hasText(extension) ? "." + extension.toLowerCase() : "";
    }
}
