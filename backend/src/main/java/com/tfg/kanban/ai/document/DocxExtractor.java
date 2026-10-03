package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.common.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Lee un Word (.docx) con Apache Tika.
 *
 * <p>Tika entrega el documento como XHTML: parrafos, titulos, listas y, sobre
 * todo, tablas de verdad ({@code table/tr/td}), porque en un .docx una tabla es un
 * elemento real y no texto colocado. Un manejador propio las recoge con su
 * estructura en vez de aplanarlas, y a la vez las imagenes incrustadas, que Tika
 * va ofreciendo como documentos embebidos.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DocxExtractor {

    static {
        // Una bomba zip es un fichero chico que al descomprimirse llena la memoria: POI ya
        // vigila la proporcion; aqui se pone ademas un tope al tamano de cada parte.
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    /** Imagenes que se guardan para elegir entre ellas: mas que las que luego se analizan, pero con tope. */
    private static final int MAX_IMAGE_CANDIDATES = 60;

    private final AiProperties properties;
    /** Cargar la lista de parsers de Tika es lo caro; el parser en si no guarda estado y se comparte. */
    private final AutoDetectParser parser = new AutoDetectParser();

    RawDocument extract(ValidatedUpload upload) {
        AiProperties.Document config = properties.getDocument();
        List<ExtractedTable> tables = new ArrayList<>();
        // El doble del tope de texto: se corta un fichero descomunal sin dejarlo parsear entero.
        StructuredHandler handler = new StructuredHandler(tables, config.getMaxTextChars() * 2);
        ImageCollector images = new ImageCollector();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, images);
        List<String> warnings = new ArrayList<>();

        // TikaInputStream, y cerrado: si el parser necesita un fichero temporal lo crea y lo borra aqui.
        try (TikaInputStream input = TikaInputStream.get(upload.bytes())) {
            parser.parse(input, handler, metadata, context);
        } catch (EncryptedDocumentException ex) {
            throw new BusinessRuleException("El Word esta protegido con contrasena: quitasela y vuelve a subirlo");
        } catch (TikaException | SAXException | IOException | RuntimeException ex) {
            // Tika envuelve lo que lanza el manejador: el limite de texto no es un error, es un corte.
            if (!hasCause(ex, StructuredHandler.LimitReached.class)) {
                log.warn("No se ha podido leer el Word {}: {}", upload.filename(), ex.toString());
                throw invalidWord();
            }
            warnings.add("El documento es muy largo: se ha leido solo el principio.");
        }
        // Un .docx es un zip: si Tika lo ha tratado como un zip cualquiera, no es un Word.
        String parsedAs = metadata.get(Metadata.CONTENT_TYPE);
        if (parsedAs == null || !parsedAs.contains("wordprocessingml")) {
            log.warn("El fichero {} no se ha leido como Word sino como {}", upload.filename(), parsedAs);
            throw invalidWord();
        }
        handler.finish();

        ImagePreparer preparer = new ImagePreparer(config);
        List<VisualCandidate> visuals = new ArrayList<>();
        int skipped = pickImages(images.images, preparer, config, visuals, warnings);

        return new RawDocument(DocumentFormat.DOCX, upload.filename(), upload.bytes().length,
                pagesOf(metadata), titleOf(metadata), TextCleaner.clean(handler.text()), tables, visuals,
                0, skipped, warnings);
    }

    private static BusinessRuleException invalidWord() {
        return new BusinessRuleException("No se ha podido leer el documento: el Word esta danado o no es valido");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    /** Las mas grandes que pasan el filtro local, hasta el tope. */
    private int pickImages(List<byte[]> candidates, ImagePreparer preparer, AiProperties.Document config,
                           List<VisualCandidate> visuals, List<String> warnings) {
        List<BufferedImage> decoded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (byte[] bytes : candidates) {
            if (!seen.add(HexFormat.of().formatHex(sha(bytes)))) continue;
            // Null: iconos, adornos o formatos que Java no lee (EMF/WMF). No aportan o no se pueden mandar.
            BufferedImage image = preparer.decode(bytes);
            if (image != null && preparer.hasContent(image)) decoded.add(image);
        }
        decoded.sort(Comparator.comparingLong((BufferedImage image) -> (long) image.getWidth() * image.getHeight())
                .reversed());

        int kept = Math.min(decoded.size(), config.getMaxImages());
        for (int i = 0; i < kept; i++) {
            visuals.add(preparer.prepare(VisualCandidate.Kind.EMBEDDED_IMAGE, 0, decoded.get(i)));
        }
        int skipped = decoded.size() - kept;
        if (skipped > 0) {
            warnings.add("El documento tiene mas imagenes de las que se analizan: se han tomado las %d mas grandes."
                    .formatted(config.getMaxImages()));
        }
        return skipped;
    }

    private static byte[] sha(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int pagesOf(Metadata metadata) {
        for (String key : new String[] {"xmpTPg:NPages", "meta:page-count"}) {
            String value = metadata.get(key);
            if (value != null) {
                try {
                    return Math.max(0, Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    // otro formato: se prueba con la siguiente clave
                }
            }
        }
        return 0;
    }

    private static String titleOf(Metadata metadata) {
        String title = metadata.get(TikaCoreProperties.TITLE);
        return title == null || title.isBlank() ? null : TextCleaner.clean(title);
    }

    /** Se queda con los bytes de cada imagen incrustada; descodificarlas viene despues, ya filtradas. */
    private static final class ImageCollector implements EmbeddedDocumentExtractor {
        private final List<byte[]> images = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return images.size() < MAX_IMAGE_CANDIDATES;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws IOException {
            byte[] bytes = stream.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length > 0 && bytes.length <= MAX_IMAGE_BYTES) images.add(bytes);
        }
    }

    /**
     * Convierte el XHTML de Tika en el texto del enunciado: un parrafo por linea,
     * titulos con {@code ##}, listas con {@code -} y cada tabla como {@code [Tabla n]}
     * mas su Markdown aparte. Las tablas anidadas se aplanan dentro de la celda de la
     * exterior.
     */
    static final class StructuredHandler extends DefaultHandler {

        /** Se corta el parseo: el documento es mas largo de lo que se admite. */
        static final class LimitReached extends SAXException {
            LimitReached() {
                super("limite de texto");
            }
        }

        private final List<ExtractedTable> tables;
        private final int hardLimit;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder paragraph = new StringBuilder();
        private String prefix = "";
        private int skipDepth;
        private int tableDepth;
        private List<List<String>> rows;
        private List<String> row;
        private StringBuilder cell;

        StructuredHandler(List<ExtractedTable> tables, int hardLimit) {
            this.tables = tables;
            this.hardLimit = hardLimit;
        }

        String text() {
            return text.toString();
        }

        void finish() {
            flushParagraph();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "head", "style", "script", "title" -> skipDepth++;
                case "table" -> {
                    if (++tableDepth == 1) {
                        flushParagraph();
                        rows = new ArrayList<>();
                    }
                }
                case "tr" -> {
                    if (tableDepth == 1) row = new ArrayList<>();
                }
                case "td", "th" -> {
                    if (tableDepth == 1) cell = new StringBuilder();
                }
                case "h1" -> prefix = "## ";
                case "h2" -> prefix = "### ";
                case "h3", "h4", "h5", "h6" -> prefix = "#### ";
                case "li" -> prefix = "- ";
                default -> {
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (localName) {
                case "head", "style", "script", "title" -> skipDepth = Math.max(0, skipDepth - 1);
                // Solo las de la tabla exterior: cerrar la celda de una anidada no cierra la de fuera.
                case "td", "th" -> {
                    if (tableDepth == 1) {
                        if (cell != null && row != null) row.add(cell.toString());
                        cell = null;
                    }
                }
                case "tr" -> {
                    if (tableDepth == 1) {
                        if (row != null && rows != null) rows.add(row);
                        row = null;
                    }
                }
                case "table" -> {
                    if (tableDepth == 1) finishTable();
                    tableDepth = Math.max(0, tableDepth - 1);
                }
                case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "br" -> endBlock();
                default -> {
                }
            }
            if (text.length() > hardLimit) throw new LimitReached();
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (skipDepth > 0) return;
            (tableDepth >= 1 && cell != null ? cell : paragraph).append(chars, start, length);
        }

        private void endBlock() {
            if (tableDepth >= 1) {
                if (cell != null) cell.append(' ');
            } else {
                flushParagraph();
            }
        }

        private void flushParagraph() {
            String content = paragraph.toString().strip();
            paragraph.setLength(0);
            if (!content.isEmpty()) text.append(prefix).append(content).append('\n');
            prefix = "";
        }

        private void finishTable() {
            String markdown = rows == null ? null : MarkdownTables.render(rows);
            rows = null;
            if (markdown == null) return;
            tables.add(new ExtractedTable(0, markdown));
            text.append("[Tabla ").append(tables.size()).append("]\n");
        }
    }
}
