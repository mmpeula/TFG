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
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validaciones del almacenamiento de fotos (de perfil y de espacio): son la
 * primera linea de defensa frente a ficheros que no son lo que dicen ser, asi
 * que interesa cubrir todos los caminos de error.
 */
class FileStorageServiceTest {

    /** Un PNG real de 1x1 pixel: lo que cuenta es lo que dicen sus bytes, no su nombre. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @TempDir
    Path tempDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setUploadDir(tempDir.toString());
        properties.setMaxFileSize(1024);

        storage = new FileStorageService(properties);
        storage.init();
    }

    private MockMultipartFile image(String name, String declaredType, byte[] content) {
        return new MockMultipartFile("file", name, declaredType, content);
    }

    @Test
    @DisplayName("Guarda la imagen con un nombre aleatorio y la extension de lo que es de verdad")
    void storesImage() {
        FileStorageService.StoredImage stored = storage.storeImage(image("foto.jpg", "image/jpeg", PNG));

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.storedName()).endsWith(".png").doesNotContain("foto");
        assertThat(tempDir.resolve(stored.storedName())).exists();
    }

    @Test
    @DisplayName("Se rechaza una imagen vacia o ausente")
    void rejectsEmptyImage() {
        assertThatThrownBy(() -> storage.storeImage(image("vacia.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vacío");
        assertThatThrownBy(() -> storage.storeImage(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Se rechaza una imagen que supera el tamano maximo")
    void rejectsTooLargeImage() {
        assertThatThrownBy(() -> storage.storeImage(image("grande.png", "image/png", new byte[2048])))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tamaño máximo");
    }

    @Test
    @DisplayName("Un texto que dice ser PNG no pasa: manda el contenido, no la cabecera")
    void rejectsFakeImage() {
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.storeImage(image("foto.png", "image/png", html)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PNG, JPEG, GIF o WebP");
    }

    @Test
    @DisplayName("Se puede leer el contenido de una imagen guardada")
    void loadsStoredImage() throws IOException {
        String stored = storage.storeImage(image("foto.png", "image/png", PNG)).storedName();

        var resource = storage.loadAsResource(stored);

        assertThat(resource.exists()).isTrue();
        try (InputStream input = resource.getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(PNG);
        }
    }

    @Test
    @DisplayName("Leer un fichero que ya no esta en disco da 404")
    void loadMissingFileFails() {
        assertThatThrownBy(() -> storage.loadAsResource("no-existe.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("No se puede salir del directorio de subidas con una ruta relativa")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> storage.loadAsResource("../fuera.png"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Borrar es idempotente: no falla si el fichero ya no esta")
    void deleteIsIdempotent() {
        String stored = storage.storeImage(image("temporal.png", "image/png", PNG)).storedName();

        storage.delete(stored);
        assertThat(tempDir.resolve(stored)).doesNotExist();

        assertThatCode(() -> storage.delete(stored)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("nunca-existio.png")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El nombre original se sanea antes de usarlo")
    void cleansFilename() {
        assertThat(FileStorageService.cleanFilename("/etc/passwd")).isEqualTo("passwd");
        assertThat(FileStorageService.cleanFilename("informe \"final\".pdf")).isEqualTo("informe final.pdf");
        assertThat(FileStorageService.cleanFilename("salto\nlinea.txt")).isEqualTo("saltolinea.txt");
        assertThat(FileStorageService.cleanFilename(null)).isEqualTo("documento");
        assertThat(FileStorageService.cleanFilename("   ")).isEqualTo("documento");
    }
}
