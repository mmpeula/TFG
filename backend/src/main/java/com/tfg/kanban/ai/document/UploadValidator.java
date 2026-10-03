package com.tfg.kanban.ai.document;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.storage.FileStorageService;
import com.tfg.kanban.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Decide si lo que se ha subido es un documento que se sabe leer.
 *
 * <p>No se fia ni de la extension ni del {@code Content-Type} que manda el
 * navegador, que los pone quien sube el fichero: el formato lo dicen los
 * primeros bytes, que detecta Tika. La extension solo sirve para cazar una
 * incoherencia («guion.pdf» que en realidad es otra cosa) y avisar de ella.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadValidator {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final List<String> TEXT_EXTENSIONS = List.of("txt", "md", "markdown", "csv", "text");
    private static final int MAX_FILENAME_CHARS = 120;
    private static final long MEGABYTE = 1024 * 1024;

    private final StorageProperties storage;
    private final Tika tika = new Tika();

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null) {
            throw new BusinessRuleException("No se ha recibido ningun documento");
        }
        if (file.isEmpty()) {
            throw new BusinessRuleException("El documento esta vacio (0 bytes). Comprueba que has subido el fichero correcto.");
        }
        if (file.getSize() > storage.getMaxFileSize()) {
            throw new BusinessRuleException("El documento pesa %.1f MB y el maximo es %d MB"
                    .formatted(file.getSize() / (double) MEGABYTE, storage.getMaxFileSize() / MEGABYTE));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            log.warn("No se ha podido leer la subida: {}", ex.getMessage());
            throw new BusinessRuleException("No se ha podido leer el documento: la subida se ha interrumpido");
        }

        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        // Solo los bytes, sin el nombre: con el nombre de pista Tika se fiaria de la extension.
        String mime = tika.detect(bytes);

        DocumentFormat format = formatOf(mime, extension);
        return new ValidatedUpload(bytes, filename, format, mime, sha256(bytes));
    }

    private DocumentFormat formatOf(String mime, String extension) {
        if ("application/pdf".equals(mime)) {
            requireExtension(extension, "PDF", "pdf");
            return DocumentFormat.PDF;
        }
        if (DOCX_MIME.equals(mime) || ("docx".equals(extension) && isGenericOffice(mime))) {
            requireExtension(extension, "Word (.docx)", "docx");
            return DocumentFormat.DOCX;
        }
        if (mime.startsWith("text/")) {
            if (!extension.isBlank() && !TEXT_EXTENSIONS.contains(extension)) {
                throw new BusinessRuleException(
                        "El fichero se llama .%s pero su contenido es texto plano. Sube el guion en PDF, Word (.docx) o texto (.txt)."
                                .formatted(extension));
            }
            return DocumentFormat.TEXT;
        }
        if ("doc".equals(extension) || "application/x-tika-msoffice".equals(mime)) {
            throw new BusinessRuleException(
                    "El formato .doc antiguo no se admite: guarda el documento como .docx o PDF");
        }
        if (mime.startsWith("image/")) {
            throw new BusinessRuleException(
                    "Una imagen suelta no se admite como documento. Pegala dentro de un PDF o de un Word y sube ese.");
        }
        throw new BusinessRuleException("Formato no admitido (%s). Sube el guion en PDF, Word (.docx) o texto plano."
                .formatted(extension.isBlank() ? mime : extension));
    }

    /** Un .docx se puede detectar como ZIP generico u OOXML sin concretar: la extension desempata. */
    private static boolean isGenericOffice(String mime) {
        return "application/zip".equals(mime) || "application/x-tika-ooxml".equals(mime);
    }

    private static void requireExtension(String extension, String label, String expected) {
        if (!extension.isBlank() && !extension.equals(expected)) {
            throw new BusinessRuleException(
                    "El fichero se llama .%s pero su contenido es %s. Cambiale la extension o sube el original."
                            .formatted(extension, label));
        }
    }

    /**
     * Solo para mostrarlo y registrarlo, nunca para escribir un fichero: sin
     * rutas, sin saltos de linea (el nombre acaba en el log y en las cabeceras) y
     * con un largo razonable.
     */
    static String safeFilename(String original) {
        String name = FileStorageService.cleanFilename(original).replaceAll("\\p{Cntrl}", "");
        if (name.length() > MAX_FILENAME_CHARS) {
            name = name.substring(0, MAX_FILENAME_CHARS);
        }
        return StringUtils.hasText(name) ? name : "documento";
    }

    private static String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return extension == null ? "" : extension.toLowerCase();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
