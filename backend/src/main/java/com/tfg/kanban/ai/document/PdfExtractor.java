package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.common.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Lee un PDF con PDFBox, pagina a pagina.
 *
 * <p>Tika no sirve de entrada para el PDF: devuelve el documento como un unico
 * flujo de texto y aqui hace falta lo contrario, saber que pagina tiene texto y
 * cual es una foto, donde estan las columnas de una tabla y que imagenes lleva.
 * Eso solo lo da el modelo de paginas de PDFBox. Tika sigue haciendo la deteccion
 * del formato y los demas formatos (ver {@link UploadValidator}).</p>
 *
 * <p>Cada pagina cae en una de estas: con texto (se lee), escaneada o sin texto
 * fiable (se dibuja para leerla a la vista), en blanco (se ignora).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PdfExtractor {

    static {
        // Dibujar una pagina no necesita pantalla; sin esto algunos servidores fallan.
        System.setProperty("java.awt.headless", "true");
    }

    /** Una imagen con un lado de al menos esto ocupa media pagina: es la pagina escaneada. */
    private static final int SCAN_MIN_SIDE = 400;
    /** Una pagina sin texto ni imagen pero con tanto contenido grafico es un dibujo vectorial, no una hoja en blanco. */
    private static final int VECTOR_CONTENT_BYTES = 1_500;
    /** Cuantas imagenes se descodifican como mucho para encontrar las que valen: descodificar cuesta memoria. */
    private static final int DECODE_ATTEMPTS_PER_KEPT = 3;
    /** Una linea que se repite en tantas paginas es cabecera o pie, no enunciado. */
    private static final double REPEATED_LINE_SHARE = 0.5;
    private static final int REPEATED_LINE_MIN_PAGES = 3;
    private static final int REPEATED_LINE_MAX_CHARS = 100;

    private static final String PAGE_NUMBER = "(?:p[aá]g(?:ina)?\\.?\\s*)?\\d+(?:\\s*(?:de|/)\\s*\\d+)?";
    private static final Pattern PAGE_NUMBER_ONLY =
            Pattern.compile("(?i)^\\W*" + PAGE_NUMBER + "\\W*$");
    private static final Pattern TRAILING_PAGE_NUMBER =
            Pattern.compile("(?i)\\s+" + PAGE_NUMBER + "$");
    private static final Pattern LEADING_PAGE_NUMBER =
            Pattern.compile("(?i)^" + PAGE_NUMBER + "\\s+");

    private final AiProperties properties;

    RawDocument extract(ValidatedUpload upload) {
        AiProperties.Document config = properties.getDocument();
        List<String> warnings = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(upload.bytes())) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new BusinessRuleException("El PDF está protegido y no permite extraer su contenido");
            }
            int total = document.getNumberOfPages();
            if (total == 0) {
                throw new BusinessRuleException("El PDF no tiene páginas");
            }
            int limit = Math.min(total, config.getMaxPages());
            if (limit < total) {
                warnings.add("El PDF tiene %d páginas: solo se han leído las %d primeras.".formatted(total, limit));
            }

            // PDFBox no avisa de las paginas sin contenido: las que faltan son paginas vacias.
            Map<Integer, PageLayoutResult> laidOut = new HashMap<>();
            layoutOf(document, limit).forEach(result -> laidOut.put(result.number(), result));
            List<PageLayoutResult> pages = IntStream.rangeClosed(1, limit)
                    .mapToObj(n -> laidOut.getOrDefault(n, new PageLayoutResult(n, List.of(), 0, false, false)))
                    .toList();
            ImagePreparer preparer = new ImagePreparer(config);

            List<Integer> scanned = new ArrayList<>();
            List<Integer> suspectTables = new ArrayList<>();
            Map<Integer, PageLayoutResult> kept = new HashMap<>();
            int blank = 0;
            for (PageLayoutResult page : pages) {
                PDPage pdPage = document.getPage(page.number() - 1);
                boolean hasText = page.meaningfulChars() >= config.getMinPageChars() && !page.garbage();
                if (hasText) {
                    kept.put(page.number(), page);
                    if (page.suspectTable()) suspectTables.add(page.number());
                } else if (page.garbage() || isImageOrDrawingPage(pdPage)) {
                    scanned.add(page.number());
                } else if (page.meaningfulChars() > 0) {
                    kept.put(page.number(), page);
                } else {
                    blank++;
                }
            }

            List<VisualCandidate> visuals = new ArrayList<>();
            int skipped = pickPagesToRender(document, preparer, config, scanned, suspectTables, visuals, warnings);
            skipped += pickEmbeddedImages(document, preparer, config, kept.keySet(), visuals, warnings);

            List<ExtractedTable> tables = new ArrayList<>();
            String text = assemble(pages, kept, tables);

            return new RawDocument(DocumentFormat.PDF, upload.filename(), upload.bytes().length, total,
                    titleOf(document), TextCleaner.clean(text), tables, visuals, blank, skipped, warnings);

        } catch (InvalidPasswordException ex) {
            throw new BusinessRuleException("El PDF está protegido con contraseña: quítasela y vuelve a subirlo");
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("No se ha podido leer el PDF {}: {}", upload.filename(), ex.toString());
            throw new BusinessRuleException("No se ha podido leer el documento: el PDF está dañado o no es válido");
        }
    }

    // ------------------------------------------------------------------ texto

    private record PageLayoutResult(int number, List<PdfLayout.Block> blocks, int meaningfulChars,
                                    boolean garbage, boolean suspectTable) {
    }

    private List<PageLayoutResult> layoutOf(PDDocument document, int limit) throws IOException {
        List<PageLayoutResult> results = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writePage() {
                List<TextPosition> characters = new ArrayList<>();
                charactersByArticle.forEach(characters::addAll);
                List<PdfLayout.Block> blocks = PdfLayout.blocks(PdfLayout.lines(characters));

                StringBuilder plain = new StringBuilder();
                boolean suspect = false;
                for (PdfLayout.Block block : blocks) {
                    switch (block) {
                        case PdfLayout.Plain p -> plain.append(p.text()).append('\n');
                        case PdfLayout.Table t -> {
                            t.asLines().forEach(line -> plain.append(line).append('\n'));
                            suspect |= !t.confident();
                        }
                    }
                }
                String text = plain.toString();
                results.add(new PageLayoutResult(getCurrentPageNo(), blocks, TextCleaner.meaningfulChars(text),
                        TextCleaner.looksLikeGarbage(text), suspect));
            }
        };
        stripper.setStartPage(1);
        stripper.setEndPage(limit);
        stripper.writeText(document, Writer.nullWriter());
        return results;
    }

    /** Une las paginas: las tablas seguras pasan a {@code tables} y dejan una marca en el texto. */
    private String assemble(List<PageLayoutResult> pages, Map<Integer, PageLayoutResult> kept,
                            List<ExtractedTable> tables) {
        Set<String> repeated = repeatedLines(pages, kept);
        StringBuilder text = new StringBuilder();
        for (PageLayoutResult page : pages) {
            if (!kept.containsKey(page.number())) continue;
            for (PdfLayout.Block block : page.blocks()) {
                switch (block) {
                    case PdfLayout.Plain p -> {
                        if (!repeated.contains(lineKey(p.text()))) text.append(p.text()).append('\n');
                    }
                    case PdfLayout.Table t -> {
                        String markdown = t.confident() ? MarkdownTables.render(t.rows()) : null;
                        if (markdown == null) {
                            t.asLines().forEach(line -> text.append(line).append('\n'));
                        } else {
                            tables.add(new ExtractedTable(page.number(), markdown));
                            text.append("[Tabla ").append(tables.size()).append("]\n");
                        }
                    }
                }
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Cabeceras, pies y numeros de pagina: lineas cortas que salen en la mitad de
     * las paginas o mas. Se cuentan sin las cifras, para que «Pagina 3 de 10» y
     * «Pagina 4 de 10» sean la misma linea.
     */
    private Set<String> repeatedLines(List<PageLayoutResult> pages, Map<Integer, PageLayoutResult> kept) {
        if (kept.size() < REPEATED_LINE_MIN_PAGES) return Set.of();
        Map<String, Integer> pagesWithLine = new HashMap<>();
        for (PageLayoutResult page : pages) {
            if (!kept.containsKey(page.number())) continue;
            page.blocks().stream()
                    .filter(PdfLayout.Plain.class::isInstance).map(block -> ((PdfLayout.Plain) block).text())
                    .filter(line -> line.length() <= REPEATED_LINE_MAX_CHARS)
                    .map(PdfExtractor::lineKey).distinct()
                    .forEach(key -> pagesWithLine.merge(key, 1, Integer::sum));
        }
        double threshold = Math.max(REPEATED_LINE_MIN_PAGES, kept.size() * REPEATED_LINE_SHARE);
        Set<String> repeated = new HashSet<>();
        pagesWithLine.forEach((key, count) -> {
            if (count >= threshold && !key.isBlank()) repeated.add(key);
        });
        return repeated;
    }

    /**
     * La linea sin su numero de pagina, para poder compararla con la de otra pagina.
     * Solo se quita un numero de pagina de verdad (suelto, o pegado al principio o al
     * final): «Apartado 3: ...» y «Apartado 4: ...» son lineas distintas aunque se parezcan.
     */
    static String lineKey(String line) {
        String key = line.trim();
        if (PAGE_NUMBER_ONLY.matcher(key).matches()) return "#";
        return LEADING_PAGE_NUMBER.matcher(TRAILING_PAGE_NUMBER.matcher(key).replaceAll("")).replaceAll("");
    }

    private static String titleOf(PDDocument document) {
        String title = document.getDocumentInformation() == null ? null : document.getDocumentInformation().getTitle();
        return title == null || title.isBlank() ? null : TextCleaner.clean(title);
    }

    // ------------------------------------------------------------- lo visual

    private boolean isImageOrDrawingPage(PDPage page) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        collectImages(page.getResources(), images, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (images.stream().anyMatch(image -> Math.min(image.getWidth(), image.getHeight()) >= SCAN_MIN_SIDE)) {
            return true;
        }
        try (InputStream contents = page.getContents()) {
            return contents != null && contents.readNBytes(VECTOR_CONTENT_BYTES).length >= VECTOR_CONTENT_BYTES;
        }
    }

    /**
     * Elige que paginas se dibujan: primero las escaneadas, que no tienen otra
     * lectura, y con lo que sobre las que tienen una tabla dudosa. Devuelve cuantas
     * se han quedado fuera por el limite.
     */
    private int pickPagesToRender(PDDocument document, ImagePreparer preparer, AiProperties.Document config,
                                  List<Integer> scanned, List<Integer> suspectTables,
                                  List<VisualCandidate> visuals, List<String> warnings) {
        List<Integer> wanted = new ArrayList<>(scanned);
        wanted.addAll(suspectTables);
        int budget = config.getMaxVisualPages();
        int skipped = Math.max(0, wanted.size() - budget);

        PDFRenderer renderer = new PDFRenderer(document);
        for (int n = 0; n < Math.min(budget, wanted.size()); n++) {
            int page = wanted.get(n);
            VisualCandidate.Kind kind = scanned.contains(page)
                    ? VisualCandidate.Kind.SCANNED_PAGE : VisualCandidate.Kind.TABLE_PAGE;
            try {
                float widthPoints = document.getPage(page - 1).getCropBox().getWidth();
                float heightPoints = document.getPage(page - 1).getCropBox().getHeight();
                float longestInches = Math.max(widthPoints, heightPoints) / 72f;
                float dpi = Math.min(config.getRenderDpi(), config.getMaxImageSide() / longestInches);
                BufferedImage image = renderer.renderImageWithDPI(page - 1, dpi, ImageType.RGB);
                visuals.add(preparer.prepare(kind, page, image));
            } catch (IOException | RuntimeException ex) {
                log.warn("No se ha podido dibujar la pagina {}: {}", page, ex.toString());
                warnings.add("No se ha podido dibujar la página %d para leerla visualmente.".formatted(page));
            }
        }
        if (skipped > 0) {
            warnings.add("Hay %d páginas que solo se pueden leer visualmente y el límite es %d: el resto se ha omitido."
                    .formatted(wanted.size(), budget));
        }
        return skipped;
    }

    private record ImageRef(int page, PDImageXObject image) {
        long area() {
            return (long) image.getWidth() * image.getHeight();
        }
    }

    /** Las imagenes de las paginas con texto que pasan el filtro local, de mas a menos grandes, hasta el tope. */
    private int pickEmbeddedImages(PDDocument document, ImagePreparer preparer, AiProperties.Document config,
                                   Set<Integer> textPages, List<VisualCandidate> visuals, List<String> warnings) {
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ImageRef> refs = new ArrayList<>();
        for (int page : textPages.stream().sorted().toList()) {
            List<PDImageXObject> images = new ArrayList<>();
            collectImages(document.getPage(page - 1).getResources(), images, seen);
            images.stream().filter(image -> preparer.worthDecoding(image.getWidth(), image.getHeight()))
                    .forEach(image -> refs.add(new ImageRef(page, image)));
        }
        refs.sort(Comparator.comparingLong(ImageRef::area).reversed());

        int kept = 0;
        int attempts = 0;
        for (ImageRef ref : refs) {
            if (kept >= config.getMaxImages() || attempts >= config.getMaxImages() * DECODE_ATTEMPTS_PER_KEPT) break;
            attempts++;
            try {
                BufferedImage decoded = ref.image().getImage();
                if (!preparer.hasContent(decoded)) continue;
                visuals.add(preparer.prepare(VisualCandidate.Kind.EMBEDDED_IMAGE, ref.page(), decoded));
                kept++;
            } catch (IOException | RuntimeException ex) {
                log.debug("Imagen de la pagina {} sin decodificar: {}", ref.page(), ex.toString());
            }
        }
        int skipped = Math.max(0, refs.size() - attempts);
        if (skipped > 0) {
            warnings.add("El documento tiene más imágenes de las que se analizan: se han tomado las %d más grandes."
                    .formatted(config.getMaxImages()));
        }
        return skipped;
    }

    /** Recorre los recursos de una pagina, incluidos los formularios que contienen otras imagenes. */
    private void collectImages(PDResources resources, List<PDImageXObject> out, Set<COSBase> seen) {
        if (resources == null) return;
        try {
            for (var name : resources.getXObjectNames()) {
                PDXObject object = resources.getXObject(name);
                if (object == null || !seen.add(object.getCOSObject())) continue;
                if (object instanceof PDImageXObject image && !image.isStencil()) {
                    out.add(image);
                } else if (object instanceof PDFormXObject form) {
                    collectImages(form.getResources(), out, seen);
                }
            }
        } catch (IOException ex) {
            log.debug("Recursos de pagina ilegibles: {}", ex.toString());
        }
    }
}
