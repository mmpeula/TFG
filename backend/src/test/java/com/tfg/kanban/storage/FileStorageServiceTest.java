package com.tfg.kanban.storage;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validaciones del almacenamiento de adjuntos: son la primera linea de defensa
 * frente a ficheros peligrosos, asi que interesa cubrir todos los caminos de error.
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setUploadDir(tempDir.toString());
        properties.setMaxFileSize(1024);
        properties.setAllowedContentTypes(List.of("text/plain", "application/pdf"));

        storage = new FileStorageService(properties);
        storage.init();
    }

    private MockMultipartFile file(String name, String type, String content) {
        return new MockMultipartFile("file", name, type, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Guarda el fichero con un nombre aleatorio conservando la extension")
    void storesFileWithRandomName() throws IOException {
        String stored = storage.store(file("memoria.txt", "text/plain", "contenido"));

        assertThat(stored).endsWith(".txt").doesNotContain("memoria");
        Path saved = tempDir.resolve(stored);
        assertThat(saved).exists();
        assertThat(Files.readString(saved)).isEqualTo("contenido");
    }

    @Test
    @DisplayName("Un fichero sin extension se guarda igualmente")
    void storesFileWithoutExtension() {
        String stored = storage.store(file("LEEME", "text/plain", "sin extension"));

        assertThat(stored).doesNotContain(".");
        assertThat(tempDir.resolve(stored)).exists();
    }

    @Test
    @DisplayName("Se rechaza un fichero vacio o ausente")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> storage.store(file("vacio.txt", "text/plain", "")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vacio");

        assertThatThrownBy(() -> storage.store(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Se rechaza un fichero que supera el tamano maximo")
    void rejectsTooLargeFile() {
        String big = "x".repeat(2048);

        assertThatThrownBy(() -> storage.store(file("grande.txt", "text/plain", big)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tamano maximo");
    }

    @Test
    @DisplayName("Se rechaza un tipo MIME que no esta en la lista blanca")
    void rejectsForbiddenContentType() {
        assertThatThrownBy(() -> storage.store(file("virus.exe", "application/x-msdownload", "MZ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no permitido");

        // Un fichero sin Content-Type tampoco pasa
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "x.txt", null, "hola".getBytes())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Se puede leer el contenido de un fichero guardado")
    void loadsStoredFile() throws IOException {
        String stored = storage.store(file("guion.txt", "text/plain", "enunciado"));

        var resource = storage.loadAsResource(stored);

        assertThat(resource.exists()).isTrue();
        try (InputStream input = resource.getInputStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("enunciado");
        }
    }

    @Test
    @DisplayName("Leer un fichero que ya no esta en disco da 404")
    void loadMissingFileFails() {
        assertThatThrownBy(() -> storage.loadAsResource("no-existe.txt"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Borrar es idempotente: no falla si el fichero ya no esta")
    void deleteIsIdempotent() {
        String stored = storage.store(file("temporal.txt", "text/plain", "algo"));

        storage.delete(stored);
        assertThat(tempDir.resolve(stored)).doesNotExist();

        assertThatCode(() -> storage.delete(stored)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("nunca-existio.txt")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El nombre original se sanea antes de usarlo en la cabecera de descarga")
    void cleansFilename() {
        assertThat(FileStorageService.cleanFilename("/etc/passwd")).isEqualTo("passwd");
        assertThat(FileStorageService.cleanFilename("informe \"final\".pdf")).isEqualTo("informe final.pdf");
        assertThat(FileStorageService.cleanFilename("salto\nlinea.txt")).isEqualTo("saltolinea.txt");
        assertThat(FileStorageService.cleanFilename(null)).isEqualTo("adjunto");
        assertThat(FileStorageService.cleanFilename("   ")).isEqualTo("adjunto");
    }
}
