package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.io.IOException;

/**
 * Decide en local si una imagen merece enviarse al modelo y la deja lista:
 * sin transparencia, reducida y en JPEG. Todo lo que se descarta aqui es una
 * imagen que no se paga.
 */
final class ImagePreparer {

    /** Mas alargada que esto es una linea, una cabecera o un separador, no un diagrama. */
    private static final double MAX_ASPECT_RATIO = 6.0;
    /** Menos variacion de brillo que esto es un fondo liso: no hay nada que leer. */
    private static final double MIN_LUMINANCE_SPREAD = 6.0;
    private static final int SAMPLE = 24;
    private static final float JPEG_QUALITY = 0.88f;

    private final AiProperties.Document config;

    ImagePreparer(AiProperties.Document config) {
        this.config = config;
    }

    /** Mas pixeles que esto y descodificar la imagen puede agotar la memoria del servidor. */
    private static final long MAX_PIXELS = 50_000_000L;

    /** Barato: solo mira las dimensiones, sin decodificar nada. */
    boolean worthDecoding(int width, int height) {
        if (width < config.getMinImageSide() || height < config.getMinImageSide()) return false;
        if ((long) width * height > MAX_PIXELS) return false;
        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        return ratio <= MAX_ASPECT_RATIO;
    }

    /** Ya decodificada: descarta las que son un color liso, como los fondos o los separadores. */
    boolean hasContent(BufferedImage image) {
        double sum = 0;
        double sumSquares = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int rgb = image.getRGB(x * image.getWidth() / SAMPLE, y * image.getHeight() / SAMPLE);
                double luminance = 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
                sum += luminance;
                sumSquares += luminance * luminance;
            }
        }
        int n = SAMPLE * SAMPLE;
        double mean = sum / n;
        return Math.sqrt(Math.max(0, sumSquares / n - mean * mean)) >= MIN_LUMINANCE_SPREAD;
    }

    /**
     * Descodifica una imagen de un fichero, pero mirando antes cuanto mide: el
     * tamano lo dice la cabecera, y una imagen pequena en disco puede ser enorme en memoria.
     * Null si no es una imagen que Java sepa leer (los EMF/WMF de Word, por ejemplo).
     */
    BufferedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (!worthDecoding(reader.getWidth(0), reader.getHeight(0))) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    VisualCandidate prepare(VisualCandidate.Kind kind, int page, BufferedImage source) {
        BufferedImage image = flattenAndScale(source);
        return new VisualCandidate(kind, page, toJpeg(image), image.getWidth(), image.getHeight());
    }

    /** Fondo blanco bajo la transparencia (un PNG transparente sale negro en JPEG) y tope al lado mayor. */
    private BufferedImage flattenAndScale(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double scale = longest > config.getMaxImageSide() ? (double) config.getMaxImageSide() / longest : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
            stream.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se ha podido codificar la imagen", ex);
        } finally {
            writer.dispose();
        }
    }
}
