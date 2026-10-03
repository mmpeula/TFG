package com.tfg.kanban.ai.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Documentos generados en el propio test, no ficheros de ejemplo: asi cada test
 * dice exactamente que lleva su documento y no depende de binarios guardados.
 */
final class TestDocuments {

    static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private TestDocuments() {
    }

    /** Un PDF con una pagina por argumento; cada pagina es un conjunto de lineas. */
    static byte[] pdf(String[]... pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String[] lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = 780;
                    for (String line : lines) {
                        content.beginText();
                        content.setFont(font, 11);
                        content.newLineAtOffset(50, y);
                        content.showText(line);
                        content.endText();
                        y -= 16;
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    static byte[] pdf(String text) throws Exception {
        return pdf(new String[] {text});
    }

    /** Un PDF de una sola pagina con varias lineas. */
    static byte[] pdfLines(String... lines) throws Exception {
        return pdf(new String[][] {lines});
    }

    /** Un PDF con un parrafo y una tabla colocada por coordenadas, como la saca un procesador de textos. */
    static byte[] pdfWithTable(String intro, String[][] rows, float[] columnX) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.newLineAtOffset(50, 780);
                content.showText(intro);
                content.endText();
                float y = 740;
                for (String[] row : rows) {
                    for (int column = 0; column < row.length; column++) {
                        if (row[column].isEmpty()) continue;
                        content.beginText();
                        content.setFont(font, 11);
                        content.newLineAtOffset(columnX[column], y);
                        content.showText(row[column]);
                        content.endText();
                    }
                    y -= 18;
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Paginas que son solo una imagen grande, sin una letra de texto: lo que da un escaner. */
    static byte[] pdfScanned(int pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(document, diagram(900, 1200, i));
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Texto normal mas un diagrama incrustado y un icono pequeno (que no debe contar). */
    static byte[] pdfWithImages(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject big = LosslessFactory.createFromImage(document, diagram(600, 400, 1));
            PDImageXObject icon = LosslessFactory.createFromImage(document, diagram(32, 32, 2));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.newLineAtOffset(50, 780);
                content.showText(text);
                content.endText();
                content.drawImage(big, 50, 400, 300, 200);
                content.drawImage(icon, 400, 700, 32, 32);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Una pagina que es solo un dibujo vectorial: ni texto ni imagenes, pero tampoco esta en blanco. */
    static byte[] pdfVectorDrawing() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int i = 0; i < 150; i++) {
                    content.addRect(40 + (i % 12) * 40, 100 + (i / 12) * 50, 30, 30);
                    content.fill();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Texto normal y varias imagenes distintas, grandes, en la misma pagina. */
    static byte[] pdfWithDistinctImages(String text, int images) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.newLineAtOffset(50, 780);
                content.showText(text);
                content.endText();
                for (int i = 0; i < images; i++) {
                    content.drawImage(LosslessFactory.createFromImage(document, diagram(300 + i * 40, 200 + i * 40, 10 + i)),
                            50, 100 + i * 120, 150, 100);
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** La misma imagen en dos paginas: es el logo de una cabecera, no dos figuras. */
    static byte[] pdfWithSharedImage(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDImageXObject shared = LosslessFactory.createFromImage(document, diagram(300, 200, 7));
            for (int i = 0; i < 2; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.newLineAtOffset(50, 780);
                    content.showText(text + " pagina " + (i + 1));
                    content.endText();
                    content.drawImage(shared, 50, 400, 150, 100);
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Una portada con una palabra, una pagina de texto, y el titulo en las propiedades del fichero. */
    static byte[] pdfWithTitle(String title, String cover, String body) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : new String[] {cover, body}) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.newLineAtOffset(50, 780);
                    content.showText(text);
                    content.endText();
                }
            }
            document.getDocumentInformation().setTitle(title);
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Word con propiedades: el numero de paginas que dice haber tenido al guardarse. */
    static byte[] docxWithPageCount(String text, int pages) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.getProperties().getExtendedProperties().getUnderlyingProperties().setPages(pages);
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Word con varias imagenes distintas y grandes. */
    static byte[] docxWithDistinctImages(String text, int images) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFRun run = document.createParagraph().createRun();
            run.setText(text);
            for (int i = 0; i < images; i++) {
                run.addPicture(new ByteArrayInputStream(png(diagram(300 + i * 50, 200 + i * 50, 20 + i))),
                        Document.PICTURE_TYPE_PNG, "figura" + i + ".png", Units.toEMU(150), Units.toEMU(100));
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    static byte[] pdfBlank(int pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage(PDRectangle.A4));
            document.save(out);
            return out.toByteArray();
        }
    }

    static byte[] pdfEncrypted(String text) throws Exception {
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
            StandardProtectionPolicy policy = new StandardProtectionPolicy("dueno", "secreto", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Se abre sin contrasena, pero el propietario ha prohibido copiar su contenido. */
    static byte[] pdfRestricted(String text) throws Exception {
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
            AccessPermission permissions = new AccessPermission();
            permissions.setCanExtractContent(false);
            StandardProtectionPolicy policy = new StandardProtectionPolicy("dueno", "", permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Un .doc antiguo (OLE2) minimo: lo justo para que se detecte como Word antiguo. */
    static byte[] legacyDoc() throws Exception {
        try (org.apache.poi.poifs.filesystem.POIFSFileSystem fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(new byte[200]), "WordDocument");
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    static byte[] zip() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("a.txt"));
            zip.write("hola".getBytes());
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    static byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) document.createParagraph().createRun().setText(paragraph);
            document.write(out);
            return out.toByteArray();
        }
    }

    static byte[] docxWithTable(String intro, String[][] rows) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(intro);
            XWPFTable table = document.createTable(rows.length, rows[0].length);
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length; column++) {
                    table.getRow(row).getCell(column).setText(rows[row][column]);
                }
            }
            document.createParagraph().createRun().setText("Fin del apartado");
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Word con un diagrama grande y un icono de adorno. */
    static byte[] docxWithImages(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFRun run = document.createParagraph().createRun();
            run.setText(text);
            run.addPicture(new ByteArrayInputStream(png(diagram(600, 400, 3))), Document.PICTURE_TYPE_PNG,
                    "arquitectura.png", Units.toEMU(300), Units.toEMU(200));
            run.addPicture(new ByteArrayInputStream(png(diagram(32, 32, 4))), Document.PICTURE_TYPE_PNG,
                    "icono.png", Units.toEMU(16), Units.toEMU(16));
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Rectangulos y lineas de colores distintos segun la semilla: con contenido, sin necesitar tipografias. */
    static BufferedImage diagram(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            Random random = new Random(seed);
            for (int i = 0; i < 25; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                g.fillRect(random.nextInt(width), random.nextInt(height), 10 + random.nextInt(width / 3 + 1),
                        10 + random.nextInt(height / 3 + 1));
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    static byte[] png(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
