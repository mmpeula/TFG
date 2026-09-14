package com.tfg.kanban.ai;

import com.tfg.kanban.common.BusinessRuleException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La extraccion se prueba con documentos generados en el propio test, no con
 * ficheros de ejemplo: asi el test es autosuficiente y no depende de binarios
 * guardados en el repositorio.
 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Extrae el texto de un PDF")
    void readsPdf() throws Exception {
        var file = new MockMultipartFile("file", "guion.pdf", "application/pdf",
                pdf("Practica 2: implementar una API REST de biblioteca"));

        assertThat(extractor.extract(file)).contains("API REST de biblioteca");
    }

    @Test
    @DisplayName("Extrae el texto de un Word .docx")
    void readsDocx() throws Exception {
        var file = new MockMultipartFile("file", "guion.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("Entrega: memoria de 10 paginas y presentacion"));

        assertThat(extractor.extract(file)).contains("memoria de 10 paginas");
    }

    @Test
    @DisplayName("Extrae el texto plano y normaliza los espacios sobrantes")
    void readsPlainText() {
        var file = new MockMultipartFile("file", "guion.txt", "text/plain",
                "Objetivo:    disenar   la API\n\n\n\nEntrega el 15".getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(file);

        assertThat(text).contains("Objetivo: disenar la API").doesNotContain("\n\n\n");
    }

    @Test
    @DisplayName("Un formato que no se sabe leer se rechaza explicando cuales valen")
    void rejectsUnknownFormat() {
        var file = new MockMultipartFile("file", "guion.pages", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("El .doc antiguo se rechaza con una instruccion util")
    void rejectsLegacyDoc() {
        var file = new MockMultipartFile("file", "guion.doc", "application/msword", "x".getBytes());

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(".docx");
    }

    @Test
    @DisplayName("Un documento sin texto legible se avisa en vez de mandar vacio al modelo")
    void rejectsEmptyDocument() throws Exception {
        var file = new MockMultipartFile("file", "escaneado.pdf", "application/pdf", pdf(" "));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("texto legible");
    }

    @Test
    @DisplayName("Un documento enorme se recorta para que quepa en el contexto del modelo")
    void truncatesHugeDocument() {
        String largo = "palabra ".repeat(4000);
        var file = new MockMultipartFile("file", "tocho.txt", "text/plain", largo.getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(file);

        assertThat(text).hasSizeLessThan(largo.length()).endsWith("[...documento recortado por longitud...]");
    }

    @Test
    @DisplayName("Un fichero vacio se rechaza")
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "vacio.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> extractor.extract(file)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Sin documento se avisa en vez de reventar con un nulo")
    void rejectsMissingFile() {
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No se ha recibido ningun documento");
    }

    @Test
    @DisplayName("Si el nombre no trae extension, se mira el tipo de contenido")
    void fallsBackToContentType() throws Exception {
        // Algunos navegadores suben el fichero sin nombre util; el tipo sigue llegando.
        var comoPdf = new MockMultipartFile("file", "sin-nombre", "application/pdf", pdf("Guion de la practica"));
        var comoTexto = new MockMultipartFile("file", "sin-nombre", "text/plain",
                "Guion en texto".getBytes(StandardCharsets.UTF_8));
        var comoDocx = new MockMultipartFile("file", "sin-nombre",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("Guion en Word"));

        assertThat(extractor.extract(comoPdf)).contains("Guion de la practica");
        assertThat(extractor.extract(comoTexto)).contains("Guion en texto");
        assertThat(extractor.extract(comoDocx)).contains("Guion en Word");
    }

    @Test
    @DisplayName("Sin extension ni tipo reconocible, el mensaje dice que formatos valen")
    void rejectsUnknownWithoutExtension() {
        var file = new MockMultipartFile("file", "sin-nombre", "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("application/octet-stream")
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("Un PDF danado se rechaza como ilegible, no como error interno")
    void rejectsBrokenPdf() {
        var file = new MockMultipartFile("file", "roto.pdf", "application/pdf",
                "%PDF-1.4 esto no es un pdf de verdad".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No se ha podido leer el documento");
    }

    @Test
    @DisplayName("El markdown cuenta como texto plano")
    void readsMarkdown() {
        var file = new MockMultipartFile("file", "guion.md", null,
                "# Practica 3\n\nEnunciado".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extract(file)).contains("Practica 3").contains("Enunciado");
    }
}
