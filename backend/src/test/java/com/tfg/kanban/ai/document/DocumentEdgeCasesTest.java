package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.DeepSeekClient;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockMultipartFile;

import static com.tfg.kanban.ai.document.TestDocuments.DOCX_MIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los casos raros de PDF y Word, y los limites que acotan lo que se lee y lo que se manda. */
class DocumentEdgeCasesTest {

    private static final String LONG_TEXT = "Enunciado de la practica de redes de computadores del curso 2026";

    private final AiProperties properties = new AiProperties();
    private final DeepSeekClient deepSeek = mock(DeepSeekClient.class);
    private final StorageProperties storage = new StorageProperties();
    private DocumentIngestionService service;
    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        properties.setApiKey("clave-de-prueba");
        validator = new UploadValidator(storage);
        service = new DocumentIngestionService(validator, new PdfExtractor(properties), new DocxExtractor(properties),
                new PlainTextExtractor(), new VisionAnalyzer(deepSeek, properties), new ProcessedDocumentCache(properties),
                properties);
    }

    private ProcessedDocument ingest(String name, String type, byte[] bytes) {
        return service.ingest(new MockMultipartFile("file", name, type, bytes), stage -> {
        });
    }

    private ProcessedDocument pdf(byte[] bytes) {
        return ingest("a.pdf", "application/pdf", bytes);
    }

    private void visionReplies(String answer) {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn(answer);
    }

    // ------------------------------------------------------------ tablas de PDF

    @Test
    @DisplayName("Dos columnas de texto corrido no son una tabla: las celdas serian lineas enteras")
    void twoColumnProseIsNotATable() throws Exception {
        String left = "Este es el texto de la columna izquierda del documento";
        String right = "Y este es el de la columna derecha de la misma pagina";

        ProcessedDocument document = pdf(TestDocuments.pdfWithTable("Introduccion",
                new String[][] {{left, right}, {left, right}, {left, right}, {left, right}}, new float[] {50, 320}));

        assertThat(document.tables()).isEmpty();
        assertThat(document.text()).contains("columna izquierda").contains("columna derecha");
    }

    @Test
    @DisplayName("Una cabecera con una sola fila no es una tabla de dos columnas")
    void tooSmallTableIsPlainText() throws Exception {
        ProcessedDocument document = pdf(TestDocuments.pdfWithTable(LONG_TEXT,
                new String[][] {{"Tarea", "Horas"}, {"Backend", "10"}}, new float[] {50, 250}));

        assertThat(document.tables()).isEmpty();
        assertThat(document.text()).contains("Backend 10");
    }

    @Test
    @DisplayName("Si casi todas las celdas estan vacias no hay tabla que reconstruir: queda el texto")
    void sparseGridIsPlainText() throws Exception {
        ProcessedDocument document = pdf(TestDocuments.pdfWithTable(LONG_TEXT,
                new String[][] {{"a", "", "", "", "", "f"}, {"", "b", "", "", "e", ""}, {"", "", "c", "d", "", ""},
                        {"x", "", "", "", "", "z"}}, new float[] {50, 110, 170, 230, 290, 350}));

        assertThat(document.tables()).isEmpty();
        verifyNoInteractions(deepSeek);
    }

    // ----------------------------------------------------------- paginas de PDF

    @Test
    @DisplayName("Una pagina que es solo un dibujo vectorial no esta en blanco: se lee a la vista")
    void readsVectorDrawingPages() throws Exception {
        visionReplies("=== PÁGINA 1 ===\n[Diagrama: tres modulos conectados entre si]");

        ProcessedDocument document = pdf(TestDocuments.pdfVectorDrawing());

        assertThat(document.visuallyReadPages()).isEqualTo(1);
        assertThat(document.context()).contains("tres modulos conectados");
    }

    @Test
    @DisplayName("Una portada con una sola palabra se conserva, y el titulo de las propiedades llega a los metadatos")
    void keepsCoverPagesAndTitle() throws Exception {
        ProcessedDocument document = pdf(TestDocuments.pdfWithTitle("Practica de redes", "Memoria", LONG_TEXT));

        assertThat(document.text()).contains("Memoria").contains("Enunciado de la practica");
        assertThat(document.title()).isEqualTo("Practica de redes");
        assertThat(document.context()).contains("- Titulo: Practica de redes");
    }

    @Test
    @DisplayName("Un PDF que prohibe copiar su contenido se rechaza")
    void rejectsRestrictedPdf() throws Exception {
        assertThatThrownBy(() -> pdf(TestDocuments.pdfRestricted("Contenido protegido de la practica")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("protegido");
    }

    // ------------------------------------------------------------------- imagenes

    @Test
    @DisplayName("Con mas imagenes que el tope, solo se analizan las mas grandes y se avisa")
    void limitsEmbeddedImagesInPdf() throws Exception {
        properties.getDocument().setMaxImages(1);
        visionReplies("=== IMAGEN 1 ===\n[Diagrama: la mas grande de las tres]");

        ProcessedDocument document = pdf(TestDocuments.pdfWithDistinctImages(LONG_TEXT, 3));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(),
                ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
        assertThat(document.warnings()).anyMatch(w -> w.contains("más imágenes de las que se analizan"));
    }

    @Test
    @DisplayName("La misma imagen repetida en varias paginas cuenta una sola vez")
    void deduplicatesSharedImages() throws Exception {
        visionReplies("=== IMAGEN 1 ===\n[Logotipo con el nombre de la facultad de informatica]");

        pdf(TestDocuments.pdfWithSharedImage(LONG_TEXT));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(),
                ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
    }

    @Test
    @DisplayName("Con mas imagenes que el tope en un Word, tambien se avisa")
    void limitsEmbeddedImagesInDocx() throws Exception {
        properties.getDocument().setMaxImages(1);
        visionReplies("=== IMAGEN 1 ===\n[Diagrama: la mas grande de las tres figuras]");

        ProcessedDocument document = ingest("a.docx", DOCX_MIME, TestDocuments.docxWithDistinctImages(LONG_TEXT, 3));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(),
                ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
        assertThat(document.warnings()).anyMatch(w -> w.contains("más imágenes de las que se analizan"));
    }

    // ----------------------------------------------------------------------- Word

    @Test
    @DisplayName("El numero de paginas de un Word, si lo trae, va a los metadatos")
    void readsDocxPageCount() throws Exception {
        ProcessedDocument document = ingest("a.docx", DOCX_MIME, TestDocuments.docxWithPageCount(LONG_TEXT, 7));

        assertThat(document.pages()).isEqualTo(7);
        assertThat(document.context()).contains("- Paginas: 7");
    }

    @Test
    @DisplayName("Un Word descomunal se corta al llegar al limite, sin parsearlo entero, y se avisa")
    void cutsHugeDocx() throws Exception {
        properties.getDocument().setMaxTextChars(60);
        String[] paragraphs = new String[30];
        for (int i = 0; i < paragraphs.length; i++) paragraphs[i] = "Parrafo de relleno numero " + i + " del enunciado";

        ProcessedDocument document = ingest("a.docx", DOCX_MIME, TestDocuments.docx(paragraphs));

        assertThat(document.warnings()).anyMatch(w -> w.contains("muy largo"));
        assertThat(document.text()).contains("Parrafo de relleno numero 0");
        assertThat(document.text().length()).isLessThan(300);
    }

    // ------------------------------------------------------------------ validador

    @Test
    @DisplayName("Un zip con extension .docx se trata como Word y falla al leerlo, no antes")
    void zipNamedDocxIsTreatedAsWord() throws Exception {
        assertThat(validator.validate(new MockMultipartFile("file", "a.docx", "application/zip", TestDocuments.zip())).format())
                .isEqualTo(DocumentFormat.DOCX);
        assertThatThrownBy(() -> ingest("a.docx", "application/zip", TestDocuments.zip()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No se ha podido leer");
    }

    @Test
    @DisplayName("Un zip de verdad, o un .doc antiguo, se rechazan con su motivo")
    void rejectsZipAndLegacyDoc() throws Exception {
        assertThatThrownBy(() -> ingest("a.zip", "application/zip", TestDocuments.zip()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Formato no admitido");
        assertThatThrownBy(() -> ingest("a.doc", "application/msword", TestDocuments.legacyDoc()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".docx");
    }

    @Test
    @DisplayName("Un texto sin extension se acepta, y uno con una extension que no es de texto se rechaza")
    void textWithoutOrWithOtherExtension() {
        assertThat(validator.validate(new MockMultipartFile("file", "guion", "text/plain", "hola".getBytes())).format())
                .isEqualTo(DocumentFormat.TEXT);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "p.html", "text/html", "hola".getBytes())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".html");
    }

    @Test
    @DisplayName("Un formato PDF dentro de un fichero con otra extension se rechaza explicando la incoherencia")
    void rejectsPdfWithWrongExtension() throws Exception {
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "guion.txt", "text/plain", TestDocuments.pdf("hola"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".txt").hasMessageContaining("PDF");
    }
}
