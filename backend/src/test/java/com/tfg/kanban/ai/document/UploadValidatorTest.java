package com.tfg.kanban.ai.document;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.storage.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static com.tfg.kanban.ai.document.TestDocuments.DOCX_MIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El formato lo dicen los bytes, no la extension ni el Content-Type que manda quien sube el fichero. */
class UploadValidatorTest {

    private final StorageProperties storage = new StorageProperties();
    private final UploadValidator validator = new UploadValidator(storage);

    private ValidatedUpload validate(String name, String type, byte[] bytes) {
        return validator.validate(new MockMultipartFile("file", name, type, bytes));
    }

    @Test
    @DisplayName("Reconoce el PDF, el Word y el texto por su contenido")
    void detectsFormatsByContent() throws Exception {
        assertThat(validate("a.pdf", "application/pdf", TestDocuments.pdf("hola")).format()).isEqualTo(DocumentFormat.PDF);
        assertThat(validate("a.docx", DOCX_MIME, TestDocuments.docx("hola")).format()).isEqualTo(DocumentFormat.DOCX);
        assertThat(validate("a.txt", "text/plain", "hola".getBytes()).format()).isEqualTo(DocumentFormat.TEXT);
    }

    @Test
    @DisplayName("El Content-Type del cliente no cuenta: un PDF que se declara texto sigue siendo un PDF")
    void ignoresDeclaredContentType() throws Exception {
        assertThat(validate("guion.pdf", "text/plain", TestDocuments.pdf("hola")).format()).isEqualTo(DocumentFormat.PDF);
        assertThat(validate("sin-nombre", "application/octet-stream", TestDocuments.pdf("hola")).format())
                .isEqualTo(DocumentFormat.PDF);
    }

    @Test
    @DisplayName("Una extension que no casa con el contenido se rechaza explicando la incoherencia")
    void rejectsExtensionMismatch() throws Exception {
        assertThatThrownBy(() -> validate("guion.docx", DOCX_MIME, TestDocuments.pdf("hola")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".docx").hasMessageContaining("PDF");
        assertThatThrownBy(() -> validate("guion.pdf", "application/pdf", "solo texto".getBytes()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".pdf").hasMessageContaining("texto plano");
    }

    @Test
    @DisplayName("Un fichero mas grande que el maximo se rechaza con su tamano")
    void rejectsOversizedFile() {
        storage.setMaxFileSize(1024);

        assertThatThrownBy(() -> validate("grande.txt", "text/plain", new byte[2048]))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("máximo");
    }

    @Test
    @DisplayName("Un binario con extension de texto no cuela como texto")
    void rejectsBinaryDisguisedAsText() {
        byte[] binary = new byte[300];
        binary[0] = 'M';
        binary[1] = 'Z';

        assertThatThrownBy(() -> validate("guion.txt", "text/plain", binary))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Formato no admitido");
    }

    @Test
    @DisplayName("El nombre se sanea: sin rutas, sin saltos de linea, sin comillas y con largo acotado")
    void sanitizesFilenames() {
        assertThat(UploadValidator.safeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(UploadValidator.safeFilename("a\r\nb\"c.txt")).isEqualTo("abc.txt");
        assertThat(UploadValidator.safeFilename(null)).isEqualTo("documento");
        assertThat(UploadValidator.safeFilename("x".repeat(500) + ".txt")).hasSize(120);
    }

    @Test
    @DisplayName("El hash identifica el contenido, no el nombre")
    void hashesContent() {
        ValidatedUpload a = validate("a.txt", "text/plain", "mismo contenido".getBytes());
        ValidatedUpload b = validate("b.txt", "text/plain", "mismo contenido".getBytes());
        ValidatedUpload c = validate("a.txt", "text/plain", "otro contenido".getBytes());

        assertThat(a.sha256()).isEqualTo(b.sha256()).isNotEqualTo(c.sha256()).hasSize(64);
    }
}
