package com.tfg.kanban.ai;

import com.tfg.kanban.common.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Saca el texto del documento de la actividad para poder enviarselo al modelo.
 *
 * Se admiten los formatos en los que suele venir un guion de practicas: PDF,
 * Word (.docx) y texto plano. No se hace OCR: un PDF escaneado no tiene texto
 * que extraer y se avisa de ello en vez de mandar una cadena vacia al modelo.
 */
@Slf4j
@Service
public class DocumentTextExtractor {

    /** Tope de caracteres que se envian al modelo: mas no cabe en su contexto. */
    private static final int MAX_CHARS = 12_000;

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

    private String fromDocx(InputStream input) throws IOException {
        try (XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
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
