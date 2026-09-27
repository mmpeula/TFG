package com.tfg.kanban.ai;

import com.tfg.kanban.common.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Saca el texto del documento de la actividad para poder enviarselo al modelo.
 *
 * <p>Se admiten los formatos en los que suele venir un guion de practicas: PDF,
 * Word (.docx) y texto plano. No se hace OCR: un PDF escaneado no tiene texto
 * que extraer y se avisa de ello en vez de mandar una cadena vacia al modelo.</p>
 *
 * <p>El PDF se lee con PDFBox, en texto plano: un PDF no tiene tablas de verdad,
 * solo texto colocado por coordenadas, asi que no hay estructura de filas y
 * columnas que preservar por mucho que cambie la herramienta. El Word (.docx)
 * si la tiene —una tabla en Word es un elemento real en su XML—, y por eso se
 * lee con Apache Tika pidiendole HTML: sus {@code <table>/<tr>/<td>} dejan claro
 * que dato va en que celda (por ejemplo, una tabla de plazos de entrega), en vez
 * de aplanarla a texto suelto como hacia la extraccion anterior.</p>
 */
@Slf4j
@Service
public class DocumentTextExtractor {

    /**
     * Tope de caracteres, solo como red de seguridad ante un fichero descomunal o
     * corrupto: un guion de practica normal no se acerca ni de lejos. No se pone
     * mas bajo porque el documento entero hace falta para cosas que no pasan por
     * el modelo, como buscar la fecha de inicio, que puede estar en cualquier
     * parte, no solo al principio; lo que si va acotado por su cuenta es lo que
     * se manda en cada llamada al modelo (ver AiPlanService).
     */
    private static final int MAX_CHARS = 80_000;

    private static final List<String> TEXT_EXTENSIONS = List.of("txt", "md", "markdown", "csv", "text");

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No se ha recibido ningun documento");
        }

        String extension = extensionOf(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "" : file.getContentType();

        try (InputStream input = file.getInputStream()) {
            String text;
            if ("pdf".equals(extension) || contentType.contains("pdf")) {
                text = fromPdf(input);
            } else if ("docx".equals(extension) || contentType.contains("wordprocessingml")) {
                text = fromDocx(input);
            } else if (TEXT_EXTENSIONS.contains(extension) || contentType.startsWith("text/")) {
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } else if ("doc".equals(extension)) {
                throw new BusinessRuleException(
                        "El formato .doc antiguo no se admite: guarda el documento como .docx o PDF");
            } else {
                throw new BusinessRuleException(
                        "Formato no admitido (%s). Sube el guion en PDF, Word (.docx) o texto plano."
                                .formatted(extension.isBlank() ? contentType : extension));
            }
            return clean(text, file.getOriginalFilename());

        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("No se ha podido leer el documento {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new BusinessRuleException("No se ha podido leer el documento: puede estar danado o protegido");
        }
    }

    private String fromPdf(InputStream input) throws IOException {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            if (document.isEncrypted()) {
                throw new BusinessRuleException("El PDF esta protegido con contrasena");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * A HTML, no a texto plano: {@link ToXMLContentHandler} vuelca los elementos
     * SAX tal cual, tablas incluidas. Se parsea directo, sin {@code BodyContentHandler}
     * por delante: ese filtro corta el documento a lo de dentro de {@code <body>},
     * pero de paso se come el {@code startPrefixMapping} del espacio de nombres
     * XHTML que Tika declara una sola vez al principio, y sin el, {@code <table>}
     * revienta con "Namespace ... not declared". Se filtra igual la cabecera con
     * metadatos, pero a mano, con {@link #bodyOf}.
     */
    private String fromDocx(InputStream input) throws IOException {
        ToXMLContentHandler html = new ToXMLContentHandler();
        try {
            new AutoDetectParser().parse(input, html, new Metadata(), new ParseContext());
        } catch (TikaException | SAXException ex) {
            throw new IOException(ex);
        }
        return bodyOf(html.toString());
    }

    /** Solo lo de dentro de {@code <body>}: fuera se queda la cabecera con metadatos, que no aporta nada. */
    private String bodyOf(String html) {
        int desde = html.indexOf("<body");
        int hasta = html.lastIndexOf("</body>");
        if (desde == -1 || hasta == -1) return html;
        desde = html.indexOf('>', desde) + 1;
        return html.substring(desde, hasta);
    }

    /** Normaliza espacios y corta por el tope, avisando en el propio texto. */
    private String clean(String raw, String filename) {
        String text = raw == null ? "" : raw.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();

        if (!StringUtils.hasText(text)) {
            throw new BusinessRuleException(
                    "El documento no contiene texto legible. Si es un PDF escaneado, sube una version con texto.");
        }
        if (text.length() > MAX_CHARS) {
            log.info("Documento {} recortado de {} a {} caracteres", filename, text.length(), MAX_CHARS);
            return text.substring(0, MAX_CHARS) + "\n\n[...documento recortado por longitud...]";
        }
        return text;
    }

    private String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return extension == null ? "" : extension.toLowerCase();
    }
}
