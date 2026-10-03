package com.tfg.kanban.ai.document;

import org.apache.pdfbox.text.TextPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reconstruye lineas y tablas a partir de donde esta colocado cada caracter de
 * una pagina de PDF.
 *
 * <p>Un PDF no guarda tablas: guarda letras con coordenadas. Lo que hay que hacer
 * es lo que hace el ojo: ver que varias lineas seguidas tienen sus textos
 * separados por huecos grandes y que esos textos caen siempre bajo los mismos
 * tramos horizontales. Si es asi, son columnas. Si lo parece pero no encaja del
 * todo (celdas combinadas, filas que ocupan dos lineas), se marca como dudosa
 * para que, si hay presupuesto, se lea mirando la pagina en vez de adivinar.</p>
 */
final class PdfLayout {

    /** Un hueco de mas de tantos espacios entre dos letras separa dos celdas, no dos palabras. */
    private static final float COLUMN_GAP_IN_SPACES = 3.5f;
    private static final float WORD_GAP_IN_SPACES = 0.3f;
    /** Entre dos lineas de una misma tabla no puede haber mas de tantas alturas de linea. */
    private static final float MAX_ROW_DISTANCE_IN_HEIGHTS = 3.0f;
    private static final int MAX_COLUMNS = 12;
    /** Celdas de mas de esto de media no son celdas, son lineas de un texto a dos columnas. */
    private static final int MAX_AVERAGE_CELL_CHARS = 45;
    private static final double CONFIDENT_FILL = 0.7;
    private static final double SUSPECT_FILL = 0.35;

    private PdfLayout() {
    }

    record Segment(float x0, float x1, String text) {
    }

    record Line(float y, float height, List<Segment> segments) {
        String plain() {
            return segments.stream().map(Segment::text).collect(Collectors.joining(" "));
        }
    }

    sealed interface Block permits Plain, Table {
    }

    record Plain(String text) implements Block {
    }

    /** {@code asLines} es la tabla como texto, para cuando no se fia uno de las columnas. */
    record Table(List<List<String>> rows, boolean confident, List<String> asLines) implements Block {
    }

    static List<Line> lines(List<TextPosition> positions) {
        List<TextPosition> sorted = positions.stream()
                .filter(position -> position.getUnicode() != null && !position.getUnicode().isEmpty())
                .sorted(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj))
                .toList();

        List<List<TextPosition>> groups = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        float lineY = 0;
        for (TextPosition position : sorted) {
            float tolerance = Math.max(2f, position.getHeightDir() * 0.5f);
            if (!current.isEmpty() && Math.abs(position.getYDirAdj() - lineY) > tolerance) {
                groups.add(current);
                current = new ArrayList<>();
            }
            if (current.isEmpty()) lineY = position.getYDirAdj();
            current.add(position);
        }
        if (!current.isEmpty()) groups.add(current);

        List<Line> lines = new ArrayList<>();
        for (List<TextPosition> group : groups) {
            List<Segment> segments = segments(group);
            if (segments.isEmpty()) continue;
            float height = (float) group.stream().mapToDouble(TextPosition::getHeightDir).max().orElse(10);
            lines.add(new Line(group.get(0).getYDirAdj(), Math.max(height, 1f), segments));
        }
        return lines;
    }

    /** Las letras de una linea, de izquierda a derecha, cortadas donde hay un hueco de columna. */
    private static List<Segment> segments(List<TextPosition> group) {
        List<TextPosition> ordered = group.stream().sorted(Comparator.comparing(TextPosition::getXDirAdj)).toList();
        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        float start = 0;
        float lastEnd = 0;
        boolean pendingSpace = false;

        for (TextPosition position : ordered) {
            String unicode = position.getUnicode();
            float x = position.getXDirAdj();
            if (unicode.isBlank()) {
                pendingSpace = !text.isEmpty();
                continue;
            }
            if (!text.isEmpty()) {
                float space = spaceWidth(position);
                float gap = x - lastEnd;
                if (gap > space * COLUMN_GAP_IN_SPACES) {
                    segments.add(new Segment(start, lastEnd, text.toString()));
                    text = new StringBuilder();
                } else if (pendingSpace || gap > space * WORD_GAP_IN_SPACES) {
                    text.append(' ');
                }
            }
            if (text.isEmpty()) start = x;
            text.append(unicode);
            lastEnd = x + position.getWidthDirAdj();
            pendingSpace = false;
        }
        if (!text.isEmpty()) segments.add(new Segment(start, lastEnd, text.toString()));
        return segments;
    }

    private static float spaceWidth(TextPosition position) {
        float width = position.getWidthOfSpace();
        return width > 0 && !Float.isNaN(width) ? width : Math.max(1f, position.getHeightDir() * 0.3f);
    }

    static List<Block> blocks(List<Line> lines) {
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            int end = i;
            if (lines.get(i).segments().size() >= 2) {
                end = i + 1;
                while (end < lines.size() && lines.get(end).segments().size() >= 2
                        && lines.get(end).y() - lines.get(end - 1).y()
                        <= lines.get(end).height() * MAX_ROW_DISTANCE_IN_HEIGHTS) {
                    end++;
                }
            }
            List<Line> run = lines.subList(i, Math.max(end, i + 1));
            Table table = end - i >= 2 ? asTable(run) : null;
            if (table != null) {
                blocks.add(table);
            } else {
                run.forEach(line -> blocks.add(new Plain(line.plain())));
            }
            i = Math.max(end, i + 1);
        }
        return blocks;
    }

    /** Null si lo que parecia una tabla no lo es. */
    private static Table asTable(List<Line> run) {
        List<float[]> columns = columnRanges(run);
        if (columns.size() < 2 || columns.size() > MAX_COLUMNS) return null;
        if (run.size() < 3 && columns.size() < 3) return null;

        double averageChars = run.stream().flatMap(line -> line.segments().stream())
                .mapToInt(segment -> segment.text().length()).average().orElse(0);
        if (averageChars > MAX_AVERAGE_CELL_CHARS) return null;

        List<List<String>> rows = new ArrayList<>();
        int filled = 0;
        for (Line line : run) {
            String[] cells = new String[columns.size()];
            for (Segment segment : line.segments()) {
                int column = columnOf(columns, segment);
                cells[column] = cells[column] == null ? segment.text() : cells[column] + " " + segment.text();
            }
            List<String> row = new ArrayList<>();
            for (String cell : cells) {
                row.add(cell == null ? "" : cell);
                if (cell != null) filled++;
            }
            rows.add(row);
        }

        double fill = (double) filled / ((double) run.size() * columns.size());
        if (fill < SUSPECT_FILL) return null;
        List<String> asLines = run.stream()
                .map(line -> line.segments().stream().map(Segment::text).collect(Collectors.joining(" | ")))
                .toList();
        return new Table(rows, fill >= CONFIDENT_FILL, asLines);
    }

    /** Los tramos horizontales que ocupan los textos, juntando los que se solapan: cada tramo es una columna. */
    private static List<float[]> columnRanges(List<Line> run) {
        List<float[]> ranges = run.stream().flatMap(line -> line.segments().stream())
                .map(segment -> new float[] {segment.x0(), segment.x1()})
                .sorted(Comparator.comparing(range -> range[0]))
                .collect(Collectors.toCollection(ArrayList::new));
        List<float[]> merged = new ArrayList<>();
        for (float[] range : ranges) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                float[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new float[] {range[0], range[1]});
            }
        }
        return merged;
    }

    private static int columnOf(List<float[]> columns, Segment segment) {
        float center = (segment.x0() + segment.x1()) / 2;
        for (int i = 0; i < columns.size(); i++) {
            if (center >= columns.get(i)[0] && center <= columns.get(i)[1]) return i;
        }
        return columns.size() - 1;
    }
}
