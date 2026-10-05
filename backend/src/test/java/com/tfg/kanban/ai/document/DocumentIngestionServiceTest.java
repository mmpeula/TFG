package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.AiUnavailableException;
import com.tfg.kanban.ai.DeepSeekClient;
import com.tfg.kanban.ai.ProcessingStage;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.tfg.kanban.ai.document.TestDocuments.DOCX_MIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El procesamiento de documentos de punta a punta, con documentos reales
 * generados en el test y DeepSeek simulado: aqui no hay credenciales ni red,
 * lo que se comprueba es que el servicio decide bien cuando <b>llamar</b> a la
 * vision y que con lo que devuelve (o con lo que no) construye el contexto.
 */
class DocumentIngestionServiceTest {

    private final AiProperties properties = new AiProperties();
    private final DeepSeekClient deepSeek = mock(DeepSeekClient.class);
    private final List<ProcessingStage> stages = new ArrayList<>();
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        properties.setApiKey("clave-de-prueba");
        service = new DocumentIngestionService(new UploadValidator(new StorageProperties()),
                new PdfExtractor(properties), new DocxExtractor(properties), new PlainTextExtractor(),
                new VisionAnalyzer(deepSeek, properties), new ProcessedDocumentCache(properties), properties);
    }

    private ProcessedDocument ingest(String name, String type, byte[] bytes) {
        return service.ingest(new MockMultipartFile("file", name, type, bytes), stages::add);
    }

    private ProcessedDocument ingestPdf(byte[] bytes) {
        return ingest("guion.pdf", "application/pdf", bytes);
    }

    private ProcessedDocument ingestDocx(byte[] bytes) {
        return ingest("guion.docx", DOCX_MIME, bytes);
    }

    // ------------------------------------------------------------ texto normal

    @Test
    @DisplayName("1. Un PDF con texto se lee en local, sin llamar a DeepSeek")
    void readsTextPdfLocally() throws Exception {
        ProcessedDocument document = ingestPdf(TestDocuments.pdfLines(
                "Practica 2: implementar una API REST de biblioteca", "Entrega: memoria y codigo"));

        assertThat(document.context())
                .startsWith("# ENUNCIADO")
                .contains("API REST de biblioteca").contains("Entrega: memoria y codigo")
                .contains("# METADATOS").contains("- Formato: PDF").contains("- Documento: guion.pdf");
        assertThat(document.pages()).isEqualTo(1);
        assertThat(document.warnings()).isEmpty();
        verifyNoInteractions(deepSeek);
        assertThat(stages).containsExactly(ProcessingStage.ANALYZING, ProcessingStage.EXTRACTING);
    }

    @Test
    @DisplayName("2. Un Word .docx con texto se lee en local, sin llamar a DeepSeek")
    void readsTextDocxLocally() throws Exception {
        ProcessedDocument document = ingestDocx(TestDocuments.docx("Entrega: memoria de 10 paginas", "Y una presentacion"));

        assertThat(document.context()).startsWith("# ENUNCIADO")
                .contains("memoria de 10 paginas").contains("Y una presentacion").contains("- Formato: Word (.docx)");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("El texto plano y el markdown se leen tal cual y con los espacios normalizados")
    void readsPlainTextAndMarkdown() {
        ProcessedDocument txt = ingest("guion.txt", "text/plain",
                "Objetivo:    disenar   la API\n\n\n\nEntrega el 15".getBytes(StandardCharsets.UTF_8));
        ProcessedDocument md = ingest("guion.md", null, "# Practica 3\n\nEnunciado".getBytes(StandardCharsets.UTF_8));

        assertThat(txt.text()).contains("Objetivo: disenar la API").doesNotContain("\n\n\n");
        assertThat(md.text()).contains("Practica 3").contains("Enunciado");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("Los caracteres especiales del espanol sobreviven, tambien en un texto en Windows-1252")
    void keepsSpanishCharacters() throws Exception {
        String frase = "Práctica: diseño de bases de datos, año 2026. ¿Qué se entrega? ¡Todo!";

        ProcessedDocument utf8 = ingest("a.txt", "text/plain", frase.getBytes(StandardCharsets.UTF_8));
        ProcessedDocument windows = ingest("b.txt", "text/plain", frase.getBytes(Charset.forName("windows-1252")));
        ProcessedDocument pdf = ingestPdf(TestDocuments.pdf(frase));
        ProcessedDocument docx = ingestDocx(TestDocuments.docx(frase));

        assertThat(utf8.text()).isEqualTo(frase);
        assertThat(windows.text()).isEqualTo(frase);
        assertThat(pdf.text()).contains("Práctica: diseño de bases de datos, año 2026");
        assertThat(docx.text()).isEqualTo(frase);
    }

    // ------------------------------------------------------------------ tablas

    @Test
    @DisplayName("3. Una tabla de un PDF conserva sus filas y columnas, como tabla Markdown")
    void keepsPdfTables() throws Exception {
        ProcessedDocument document = ingestPdf(TestDocuments.pdfWithTable("Reparto de horas del proyecto",
                new String[][] {{"Tarea", "Horas", "Prioridad"}, {"Backend", "10", "Alta"},
                        {"Frontend", "15", "Media"}, {"Testing", "8", "Alta"}},
                new float[] {50, 250, 400}));

        assertThat(document.tables()).hasSize(1);
        assertThat(document.tables().get(0).markdown()).isEqualTo("""
                | Tarea | Horas | Prioridad |
                | --- | --- | --- |
                | Backend | 10 | Alta |
                | Frontend | 15 | Media |
                | Testing | 8 | Alta |""");
        assertThat(document.text()).contains("Reparto de horas del proyecto").contains("[Tabla 1]")
                .doesNotContain("Backend");
        assertThat(document.context()).contains("# TABLAS").contains("## Tabla 1 (pagina 1)");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("4. Una tabla de un Word se extrae con su estructura, no aplanada")
    void keepsDocxTables() throws Exception {
        ProcessedDocument document = ingestDocx(TestDocuments.docxWithTable("Plazos de entrega",
                new String[][] {{"Sprint", "Fecha de entrega"}, {"Sprint 1", "05/10/2026"}, {"Sprint 2", "19/10/2026"}}));

        assertThat(document.tables()).singleElement().satisfies(table -> assertThat(table.markdown()).isEqualTo("""
                | Sprint | Fecha de entrega |
                | --- | --- |
                | Sprint 1 | 05/10/2026 |
                | Sprint 2 | 19/10/2026 |"""));
        assertThat(document.text()).isEqualTo("Plazos de entrega\n[Tabla 1]\nFin del apartado");
        assertThat(document.context()).contains("# TABLAS");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("Una tabla de mas no cabe en el tope de tablas: se omite entera y se avisa, no se corta a medias")
    void omitsTablesOverTheLimit() throws Exception {
        properties.getDocument().setMaxTablesChars(60);

        ProcessedDocument document = ingestDocx(TestDocuments.docxWithTable("Plazos",
                new String[][] {{"Sprint", "Fecha de entrega"}, {"Sprint 1", "05/10/2026"}, {"Sprint 2", "19/10/2026"}}));

        assertThat(document.text()).contains("[Tabla 1 omitida por longitud]");
        assertThat(document.warnings()).anyMatch(w -> w.contains("tabla 1"));
    }

    // ---------------------------------------------------------------- imagenes

    @Test
    @DisplayName("5a. Un Word con un diagrama manda a DeepSeek solo el diagrama, no el icono de adorno")
    void sendsOnlyRelevantImagesFromDocx() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("=== IMAGEN 1 ===\n[Diagrama: cliente -> API -> base de datos]");

        ProcessedDocument document = ingestDocx(TestDocuments.docxWithImages("Arquitectura del sistema"));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
        assertThat(document.visual()).singleElement()
                .satisfies(finding -> assertThat(finding.content()).contains("cliente -> API"));
        assertThat(document.context()).contains("# CONTENIDO VISUAL").contains("Imagen del documento")
                .contains("cliente -> API -> base de datos");
        assertThat(stages).contains(ProcessingStage.VISUAL);
    }

    @Test
    @DisplayName("5b. Un PDF con texto y un diagrama incrustado tambien lo analiza, y la imagen decorativa se descarta")
    void sendsOnlyRelevantImagesFromPdf() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("=== IMAGEN 1 ===\nTabla de requisitos: RF1 login, RF2 tablero");

        ProcessedDocument document = ingestPdf(TestDocuments.pdfWithImages("Requisitos del sistema de gestion de la biblioteca universitaria"));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
        assertThat(document.text()).contains("Requisitos del sistema de gestion");
        assertThat(document.context()).contains("Imagen de la pagina 1").contains("RF1 login");
    }

    @Test
    @DisplayName("Si el modelo dice que la imagen es decorativa, no entra en el contexto")
    void dropsIrrelevantImages() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("=== IMAGEN 1 ===\nIRRELEVANTE");

        ProcessedDocument document = ingestDocx(TestDocuments.docxWithImages("Portada del trabajo de fin de curso"));

        assertThat(document.visual()).isEmpty();
        assertThat(document.context()).doesNotContain("CONTENIDO VISUAL");
    }

    @Test
    @DisplayName("Si DeepSeek falla con una imagen incrustada, el documento sigue siendo util y se avisa")
    void imageFailureIsOnlyAWarning() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenThrow(new AiUnavailableException("DeepSeek esta caido"));

        ProcessedDocument document = ingestDocx(TestDocuments.docxWithImages("Arquitectura del sistema"));

        assertThat(document.text()).contains("Arquitectura del sistema");
        assertThat(document.warnings()).anyMatch(w -> w.contains("No se han podido analizar") && w.contains("caido"));
    }

    @Test
    @DisplayName("Con la vision desactivada, las imagenes se omiten avisando y el documento se lee igual")
    void imagesAreSkippedWhenVisionIsOff() throws Exception {
        properties.getDocument().setVisionEnabled(false);

        ProcessedDocument document = ingestDocx(TestDocuments.docxWithImages("Arquitectura del sistema"));

        verifyNoInteractions(deepSeek);
        assertThat(document.warnings()).anyMatch(w -> w.contains("desactivado"));
    }

    // -------------------------------------------------------------- escaneados

    @Test
    @DisplayName("6a. Un PDF escaneado se transcribe con la vision y el resultado entra como contenido visual")
    void transcribesScannedPdf() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("""
                === PÁGINA 1 ===
                Enunciado: montar una red local.
                === PÁGINA 2 ===
                | Tarea | Horas |
                | --- | --- |
                | Cableado | 10 |""");

        ProcessedDocument document = ingestPdf(TestDocuments.pdfScanned(2));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 2), anyInt());
        assertThat(document.visuallyReadPages()).isEqualTo(2);
        assertThat(document.text()).isBlank();
        assertThat(document.context()).doesNotContain("# ENUNCIADO")
                .contains("# CONTENIDO VISUAL")
                .contains("## Pagina 1 (leida visualmente: no tenia texto)").contains("montar una red local")
                .contains("| Cableado | 10 |").contains("(2 leidas visualmente)");
        assertThat(stages).containsExactly(ProcessingStage.ANALYZING, ProcessingStage.EXTRACTING, ProcessingStage.VISUAL);
    }

    @Test
    @DisplayName("Un escaneado no se paga dos veces: reintentar con el mismo fichero reutiliza lo leido")
    void reusesAlreadyReadDocument() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("=== PÁGINA 1 ===\nEnunciado de la practica de redes");
        byte[] scanned = TestDocuments.pdfScanned(1);

        ProcessedDocument first = ingestPdf(scanned);
        ProcessedDocument second = ingestPdf(scanned);

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(), anyList(), anyInt());
        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("Buscar la fecha de inicio nunca gasta vision: un escaneado sin texto simplemente no tiene fecha")
    void localContextNeverCallsTheModel() throws Exception {
        String context = service.localContext(new MockMultipartFile("file", "scan.pdf", "application/pdf",
                TestDocuments.pdfScanned(2)));

        verifyNoInteractions(deepSeek);
        assertThat(context).doesNotContain("# ENUNCIADO").contains("# METADATOS");
    }

    @Test
    @DisplayName("Un escaneado con la vision desactivada se rechaza explicando por que")
    void scannedWithVisionOffIsRejected() throws Exception {
        properties.getDocument().setVisionEnabled(false);

        assertThatThrownBy(() -> ingestPdf(TestDocuments.pdfScanned(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("escaneadas").hasMessageContaining("desactivado");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("Si DeepSeek falla con un escaneado, el error llega al usuario: no se sigue con un documento vacio")
    void scannedFailurePropagates() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenThrow(new AiUnavailableException("La cuenta de DeepSeek se ha quedado sin saldo"));

        assertThatThrownBy(() -> ingestPdf(TestDocuments.pdfScanned(1)))
                .isInstanceOf(AiUnavailableException.class).hasMessageContaining("sin saldo");
    }

    @Test
    @DisplayName("Si el modelo no consigue leer nada del escaneado, se dice en vez de mandar un documento vacio")
    void scannedUnreadableIsRejected() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("   ");

        assertThatThrownBy(() -> ingestPdf(TestDocuments.pdfScanned(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("escaneado");
    }

    @Test
    @DisplayName("Mas paginas escaneadas que el limite: se leen las primeras y se avisa de las demas")
    void limitsScannedPages() throws Exception {
        properties.getDocument().setMaxVisualPages(2);
        properties.getDocument().setImagesPerRequest(2);
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("=== PÁGINA 1 ===\nPrimera pagina de contenido\n=== PÁGINA 2 ===\nSegunda pagina de contenido");

        ProcessedDocument document = ingestPdf(TestDocuments.pdfScanned(5));

        assertThat(document.visuallyReadPages()).isEqualTo(2);
        assertThat(document.warnings()).anyMatch(w -> w.contains("5 páginas") && w.contains("límite es 2"));
    }

    @Test
    @DisplayName("Si el modelo no pone las marcas de pagina, lo leido no se pierde")
    void unmarkedAnswerIsKept() throws Exception {
        properties.getDocument().setImagesPerRequest(2);
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("Texto del enunciado leido sin separar por paginas");

        ProcessedDocument document = ingestPdf(TestDocuments.pdfScanned(2));

        assertThat(document.context()).contains("Texto del enunciado leido sin separar").contains("Lectura conjunta");
    }

    @Test
    @DisplayName("Una tabla de PDF que no se reconstruye con confianza se relee a la vista, y su texto local se conserva")
    void rereadsSuspectPdfTables() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("""
                === PÁGINA 1 ===
                | Hito | Fecha | Peso |
                | --- | --- | --- |
                | Analisis | 05/10/2026 | 20 |""");

        ProcessedDocument document = ingestPdf(TestDocuments.pdfWithTable("Calendario de hitos del proyecto final",
                new String[][] {{"Hito", "Inicio", "Fin", "Peso", "Nota"}, {"Analisis", "05/10", "", "", "20"},
                        {"", "Diseno", "12/10", "", "30"}, {"Entrega", "", "", "20/12", "50"}, {"", "Cierre", "", "", "ok"}},
                new float[] {50, 130, 210, 290, 370}));

        verify(deepSeek, times(1)).chatVision(anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 1), anyInt());
        assertThat(document.tables()).isEmpty();
        assertThat(document.text()).contains("Calendario de hitos").contains("Analisis");
        assertThat(document.context()).contains("Pagina 1 (releida visualmente por tener una tabla compleja)")
                .contains("| Analisis | 05/10/2026 | 20 |");
    }

    @Test
    @DisplayName("Si falla la relectura de una tabla dudosa, queda la lectura local y un aviso, no un error")
    void suspectTableFailureKeepsLocalText() throws Exception {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenThrow(new AiUnavailableException("DeepSeek esta caido"));

        ProcessedDocument document = ingestPdf(TestDocuments.pdfWithTable("Calendario de hitos del proyecto final",
                new String[][] {{"Hito", "Inicio", "Fin", "Peso", "Nota"}, {"Analisis", "05/10", "", "", "20"},
                        {"", "Diseno", "12/10", "", "30"}, {"Entrega", "", "", "20/12", "50"}, {"", "Cierre", "", "", "ok"}},
                new float[] {50, 130, 210, 290, 370}));

        assertThat(document.text()).contains("Analisis");
        assertThat(document.warnings()).anyMatch(w -> w.contains("caido"));
    }

    // ------------------------------------------------------ vacio, roto, formato

    @Test
    @DisplayName("7a. Un fichero vacio se rechaza")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> ingest("vacio.txt", "text/plain", new byte[0]))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("vacío");
    }

    @Test
    @DisplayName("7b. Sin documento se avisa en vez de reventar con un nulo")
    void rejectsMissingFile() {
        assertThatThrownBy(() -> service.ingest(null, stages::add))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ningún documento");
    }

    @Test
    @DisplayName("7c. Un PDF con solo paginas en blanco se rechaza diciendo que estan en blanco")
    void rejectsBlankPdf() throws Exception {
        assertThatThrownBy(() -> ingestPdf(TestDocuments.pdfBlank(3)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("3 páginas").hasMessageContaining("en blanco");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("7d. Un texto de solo espacios y un Word sin contenido se rechazan como sin texto legible")
    void rejectsTextWithoutContent() throws Exception {
        assertThatThrownBy(() -> ingest("a.txt", "text/plain", "   \n\n  ".getBytes()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("texto legible");
        assertThatThrownBy(() -> ingestDocx(TestDocuments.docx(" ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("texto legible");
    }

    @Test
    @DisplayName("Las paginas en blanco de un PDF con texto se ignoran sin molestar")
    void ignoresBlankPagesInsideAPdf() throws Exception {
        ProcessedDocument document = ingestPdf(TestDocuments.pdf(
                new String[] {"Enunciado de la practica de compiladores"}, new String[] {}, new String[] {"Entrega final"}));

        assertThat(document.blankPages()).isEqualTo(1);
        assertThat(document.context()).contains("Enunciado de la practica").contains("Entrega final")
                .contains("(1 en blanco)");
    }

    @Test
    @DisplayName("8a. Un PDF corrupto se rechaza como ilegible, no como error interno")
    void rejectsBrokenPdf() {
        assertThatThrownBy(() -> ingestPdf("%PDF-1.4 esto no es un pdf de verdad".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No se ha podido leer el documento");
    }

    @Test
    @DisplayName("8b. Un Word corrupto o truncado se rechaza como ilegible")
    void rejectsBrokenDocx() throws Exception {
        byte[] good = TestDocuments.docx("Contenido de la practica");
        byte[] truncated = java.util.Arrays.copyOf(good, good.length / 2);

        assertThatThrownBy(() -> ingestDocx(truncated))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No se ha podido leer el documento");
    }

    @Test
    @DisplayName("Un PDF protegido con contrasena se rechaza pidiendo que se la quiten")
    void rejectsEncryptedPdf() throws Exception {
        assertThatThrownBy(() -> ingestPdf(TestDocuments.pdfEncrypted("Contenido secreto")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("contraseña");
    }

    @Test
    @DisplayName("9. Un formato no admitido se rechaza explicando cuales valen")
    void rejectsUnsupportedFormats() throws Exception {
        byte[] executable = new byte[200];
        executable[0] = 'M';
        executable[1] = 'Z';

        assertThatThrownBy(() -> ingest("guion.pages", "application/octet-stream", executable))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("PDF");
        assertThatThrownBy(() -> ingest("guion.pdf", "application/pdf", executable))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Formato no admitido");
        assertThatThrownBy(() -> ingest("hoja.xlsx", "application/zip", TestDocuments.png(TestDocuments.diagram(50, 50, 1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("imagen");
    }

    @Test
    @DisplayName("El .doc antiguo se rechaza con una instruccion util")
    void rejectsLegacyDoc() {
        assertThatThrownBy(() -> ingest("guion.doc", "application/msword", "x".getBytes()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(".docx");
    }

    // ----------------------------------------------------------------- grandes

    @Test
    @DisplayName("10a. Un texto descomunal se recorta por el tope y se avisa, como red de seguridad")
    void truncatesHugeText() {
        String largo = "palabra ".repeat(11_000);

        ProcessedDocument document = ingest("tocho.txt", "text/plain", largo.getBytes(StandardCharsets.UTF_8));

        assertThat(document.text()).hasSizeLessThan(largo.length()).endsWith("[...documento recortado por longitud...]");
        assertThat(document.warnings()).anyMatch(w -> w.contains("recortado"));
    }

    @Test
    @DisplayName("Un documento largo pero normal no se recorta: hace falta entero para cosas como la fecha")
    void doesNotTruncateAnOrdinaryLongDocument() {
        String largo = "palabra ".repeat(9_000);

        ProcessedDocument document = ingest("guion.txt", "text/plain", largo.getBytes(StandardCharsets.UTF_8));

        assertThat(document.text()).doesNotContain("recortado").hasSize(largo.trim().length());
    }

    @Test
    @DisplayName("10b. Un PDF de muchas paginas se lee entero, sin cabeceras ni numeros de pagina repetidos")
    void readsLongPdfWithoutRepeatedHeaders() throws Exception {
        String[][] pages = new String[40][];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new String[] {"Universidad de Granada - Curso 2026", "Apartado " + (i + 1) + ": contenido del apartado "
                    + (i + 1) + " de la practica", "Pagina " + (i + 1) + " de 40"};
        }

        ProcessedDocument document = ingestPdf(TestDocuments.pdf(pages));

        assertThat(document.pages()).isEqualTo(40);
        assertThat(document.text()).contains("Apartado 1:").contains("Apartado 40:")
                .doesNotContain("Universidad de Granada").doesNotContain("Pagina 7 de 40");
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("10c. Mas paginas que el limite: se leen las primeras y se avisa")
    void limitsPages() throws Exception {
        properties.getDocument().setMaxPages(3);
        String[][] pages = new String[6][];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new String[] {"Contenido unico de la pagina numero " + (i + 1) + " del enunciado"};
        }

        ProcessedDocument document = ingestPdf(TestDocuments.pdf(pages));

        assertThat(document.text()).contains("pagina numero 3").doesNotContain("pagina numero 4");
        assertThat(document.warnings()).anyMatch(w -> w.contains("6 páginas") && w.contains("3 primeras"));
    }

    // -------------------------------------------------------------- seguridad

    @Test
    @DisplayName("El nombre del fichero no puede colar saltos de linea ni rutas en el contexto")
    void sanitizesFilename() {
        ProcessedDocument document = ingest("../../etc/pas\r\nswd\".txt", "text/plain", "Contenido del enunciado".getBytes());

        assertThat(document.filename()).doesNotContain("\n").doesNotContain("\r").doesNotContain("/").doesNotContain("\"");
        assertThat(document.context()).doesNotContain("../");
    }

    @Test
    @DisplayName("No se manda el autor del documento al modelo: son datos personales que no hacen falta")
    void doesNotLeakTheAuthor() throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument word = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        word.createParagraph().createRun().setText("Enunciado de la practica de sistemas operativos");
        word.getProperties().getCoreProperties().setCreator("Maria Perez Lopez");
        word.getProperties().getCoreProperties().setTitle("Practica 1");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        word.write(out);
        word.close();

        ProcessedDocument document = ingestDocx(out.toByteArray());

        assertThat(document.context()).contains("- Titulo: Practica 1").doesNotContain("Maria Perez");
    }

    @Test
    @DisplayName("El contexto de un documento sin tablas ni imagenes solo lleva enunciado y metadatos")
    void emptySectionsAreOmitted() {
        ProcessedDocument document = ingest("a.txt", "text/plain", "Solo texto del enunciado".getBytes());

        assertThat(document.context()).doesNotContain("# TABLAS").doesNotContain("# CONTENIDO VISUAL");
        verify(deepSeek, never()).chatVision(anyString(), anyString(), anyList(), anyInt());
    }
}
