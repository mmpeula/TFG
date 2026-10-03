package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las piezas pequenas del procesamiento, probadas sin pasar por un documento entero. */
class ContentUtilitiesTest {

    // ---------------------------------------------------------- MarkdownTables

    @Test
    @DisplayName("Una tabla se pinta con cabecera, separador y las celdas en su columna")
    void rendersTable() {
        String markdown = MarkdownTables.render(List.of(List.of("A", "B"), List.of("1", "2")));

        assertThat(markdown).isEqualTo("| A | B |\n| --- | --- |\n| 1 | 2 |");
    }

    @Test
    @DisplayName("Se quitan las filas y columnas vacias, y las filas cortas se rellenan")
    void dropsEmptyRowsAndColumnsAndPadsShortRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(java.util.Arrays.asList("A", "", "C"));
        rows.add(java.util.Arrays.asList("", "", ""));
        rows.add(java.util.Arrays.asList("1", null, "3"));
        rows.add(List.of("solo"));

        assertThat(MarkdownTables.render(rows)).isEqualTo("| A | C |\n| --- | --- |\n| 1 | 3 |\n| solo |  |");
    }

    @Test
    @DisplayName("Una tabla sin nada dentro no se pinta, y el pipe de una celda no rompe la tabla")
    void emptyTableIsNullAndPipesAreEscaped() {
        assertThat(MarkdownTables.render(List.of())).isNull();
        assertThat(MarkdownTables.render(List.of(List.of(" ", ""), List.of("", "  ")))).isNull();
        assertThat(MarkdownTables.render(List.of(List.of("a|b", "x\ny")))).startsWith("| a\\|b | x y |");
    }

    // -------------------------------------------------------------- TextCleaner

    @Test
    @DisplayName("Limpia ligaduras, controles, espacios y el guion de salto de palabra")
    void cleansText() {
        assertThat(TextCleaner.clean(null)).isEmpty();
        assertThat(TextCleaner.clean("oﬁcina eﬃcaz ﬀ ﬂﬄ")).isEqualTo("oficina efficaz ff flffl");
        assertThat(TextCleaner.clean("la apli-\ncacion  de\t\tdatos\u0000­​")).isEqualTo("la aplicacion de datos");
        assertThat(TextCleaner.clean("a\r\n\r\n\r\n\r\nb c")).isEqualTo("a\n\nb c");
        // un guion que separa dos palabras con mayuscula no es de salto de linea
        assertThat(TextCleaner.clean("Front-\nEnd")).isEqualTo("Front-\nEnd");
    }

    @Test
    @DisplayName("Decodifica UTF-8 con y sin BOM, y cae a Windows-1252 si no es UTF-8 valido")
    void decodesCharsets() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};

        assertThat(TextCleaner.decode(bom)).isEqualTo("hi");
        assertThat(TextCleaner.decode("año ñandú".getBytes(StandardCharsets.UTF_8))).isEqualTo("año ñandú");
        assertThat(TextCleaner.decode("año ñandú".getBytes(Charset.forName("windows-1252")))).isEqualTo("año ñandú");
        assertThat(TextCleaner.decode(new byte[] {'a'})).isEqualTo("a");
    }

    @Test
    @DisplayName("Cuenta como contenido solo letras y cifras, y detecta el texto basura de una fuente mal codificada")
    void countsContentAndDetectsGarbage() {
        assertThat(TextCleaner.meaningfulChars("a1 ,;- ñ")).isEqualTo(3);
        assertThat(TextCleaner.looksLikeGarbage("Texto normal de un enunciado de practica, con puntuacion.")).isFalse();
        assertThat(TextCleaner.looksLikeGarbage(""
                + "")).isTrue();
        assertThat(TextCleaner.looksLikeGarbage("")).isFalse();
    }

    // ----------------------------------------------------------- ImagePreparer

    private final AiProperties.Document config = new AiProperties().getDocument();
    private final ImagePreparer preparer = new ImagePreparer(config);

    @Test
    @DisplayName("Descarta por sus dimensiones: iconos, lineas alargadas y tamanos que pueden agotar la memoria")
    void filtersByDimensions() {
        assertThat(preparer.worthDecoding(600, 400)).isTrue();
        assertThat(preparer.worthDecoding(60, 400)).isFalse();
        assertThat(preparer.worthDecoding(400, 60)).isFalse();
        assertThat(preparer.worthDecoding(1_500, 150)).isFalse();
        assertThat(preparer.worthDecoding(10_000, 10_000)).isFalse();
    }

    @Test
    @DisplayName("Un fondo liso no tiene contenido; una imagen con formas, si")
    void detectsFlatImages() {
        BufferedImage flat = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        assertThat(preparer.hasContent(flat)).isFalse();
        assertThat(preparer.hasContent(TestDocuments.diagram(200, 200, 5))).isTrue();
    }

    @Test
    @DisplayName("Reduce el lado mayor al tope y sale como JPEG; sobre una imagen pequena no escala")
    void scalesAndEncodes() throws Exception {
        VisualCandidate big = preparer.prepare(VisualCandidate.Kind.EMBEDDED_IMAGE, 2, TestDocuments.diagram(3_000, 1_500, 1));
        VisualCandidate small = preparer.prepare(VisualCandidate.Kind.SCANNED_PAGE, 1, TestDocuments.diagram(400, 300, 1));

        assertThat(big.width()).isEqualTo(config.getMaxImageSide());
        assertThat(big.height()).isEqualTo(config.getMaxImageSide() / 2);
        assertThat(small.width()).isEqualTo(400);
        assertThat(big.jpeg()[0]).isEqualTo((byte) 0xFF);
        assertThat(big.jpeg()[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    @DisplayName("Decodifica una imagen valida y devuelve nulo ante basura, un icono o un formato que no se lee")
    void decodesSafely() throws Exception {
        assertThat(preparer.decode(TestDocuments.png(TestDocuments.diagram(300, 200, 1)))).isNotNull();
        assertThat(preparer.decode(TestDocuments.png(TestDocuments.diagram(30, 30, 1)))).isNull();
        assertThat(preparer.decode("esto no es una imagen".getBytes())).isNull();
        assertThat(preparer.decode(new byte[0])).isNull();
    }

    // ------------------------------------------------------- StructuredHandler

    private static void element(DocxExtractor.StructuredHandler handler, String name, String text) throws SAXException {
        handler.startElement("", name, name, new AttributesImpl());
        if (text != null) handler.characters(text.toCharArray(), 0, text.length());
        handler.endElement("", name, name);
    }

    private static void open(DocxExtractor.StructuredHandler handler, String name) {
        handler.startElement("", name, name, new AttributesImpl());
    }

    private static void close(DocxExtractor.StructuredHandler handler, String name) throws SAXException {
        handler.endElement("", name, name);
    }

    @Test
    @DisplayName("Los titulos, listas y parrafos del XHTML salen como lineas con su marca")
    void structuresHeadingsAndLists() throws Exception {
        var handler = new DocxExtractor.StructuredHandler(new ArrayList<>(), 100_000);

        open(handler, "head");
        element(handler, "title", "Metadatos que no son enunciado");
        close(handler, "head");
        element(handler, "h1", "Titulo");
        element(handler, "h2", "Subtitulo");
        element(handler, "h4", "Apartado");
        element(handler, "li", "Primer punto");
        element(handler, "p", "Un parrafo");
        open(handler, "p");
        handler.characters("con salto".toCharArray(), 0, 9);
        element(handler, "br", null);
        handler.characters("de linea".toCharArray(), 0, 8);
        close(handler, "p");
        element(handler, "span", "  ");
        handler.finish();

        assertThat(handler.text()).isEqualTo("""
                ## Titulo
                ### Subtitulo
                #### Apartado
                - Primer punto
                Un parrafo
                con salto
                de linea
                """).doesNotContain("Metadatos");
    }

    @Test
    @DisplayName("Una tabla anidada se aplana en la celda de la exterior, y las celdas con parrafos se juntan")
    void flattensNestedTables() throws Exception {
        List<ExtractedTable> tables = new ArrayList<>();
        var handler = new DocxExtractor.StructuredHandler(tables, 100_000);

        open(handler, "table");
        open(handler, "tr");
        open(handler, "th");
        element(handler, "p", "Cabecera");
        close(handler, "th");
        open(handler, "td");
        element(handler, "p", "Fila uno");
        open(handler, "table");
        open(handler, "tr");
        element(handler, "td", "interior");
        close(handler, "tr");
        close(handler, "table");
        close(handler, "td");
        close(handler, "tr");
        close(handler, "table");
        handler.finish();

        assertThat(tables).singleElement().satisfies(table -> assertThat(table.markdown()).contains("Cabecera")
                .contains("Fila uno").contains("interior"));
        assertThat(handler.text()).isEqualTo("[Tabla 1]\n");
    }

    @Test
    @DisplayName("Una tabla sin texto no deja marca, y pasarse del limite corta el parseo")
    void emptyTablesAndLimit() throws Exception {
        List<ExtractedTable> tables = new ArrayList<>();
        var handler = new DocxExtractor.StructuredHandler(tables, 20);

        open(handler, "table");
        open(handler, "tr");
        element(handler, "td", " ");
        close(handler, "tr");
        close(handler, "table");
        assertThat(tables).isEmpty();
        assertThat(handler.text()).isEmpty();

        assertThatThrownBy(() -> element(handler, "p", "un parrafo bastante mas largo que el limite de veinte"))
                .isInstanceOf(DocxExtractor.StructuredHandler.LimitReached.class);
    }
}
