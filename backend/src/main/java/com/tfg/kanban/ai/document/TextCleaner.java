package com.tfg.kanban.ai.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Deja el texto extraido en una forma estable y sin ruido, venga del formato que venga. */
final class TextCleaner {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]|\\u00AD|\\u200B|\\uFEFF");
    private static final Pattern HYPHEN_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");

    private TextCleaner() {
    }

    /**
     * Normaliza Unicode (una «ñ» o una tilde pueden venir como dos caracteres
     * sueltos), cambia las ligaduras tipograficas por sus letras, quita los
     * caracteres de control y el guion de salto de palabra, y compacta los espacios.
     */
    static String clean(String raw) {
        if (raw == null) return "";
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
                .replace("ﬀ", "ff").replace("ﬁ", "fi").replace("ﬂ", "fl")
                .replace("ﬃ", "ffi").replace("ﬄ", "ffl");
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        text = CONTROL.matcher(text).replaceAll("");
        text = HYPHEN_BREAK.matcher(text).replaceAll("$1$2");
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * UTF-8 si lo es de verdad; si no, windows-1252, que es lo que sueltan los
     * editores de Windows y donde viven las tildes de un guion en espanol.
     * Un guion mal decodificado ensucia el texto con simbolos raros sin avisar.
     */
    static String decode(byte[] bytes) {
        int offset = hasUtf8Bom(bytes) ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }

    /** Letras y cifras: lo que cuenta como contenido a la hora de decidir si una pagina «tiene texto». */
    static int meaningfulChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) count++;
        }
        return count;
    }

    /**
     * Un PDF con una fuente mal codificada «tiene texto» pero es basura: puro
     * cuadrado, simbolo y caracter de uso privado. Si la mayor parte de lo que no es
     * espacio no es una letra, ese texto no sirve y la pagina hay que leerla a la vista.
     */
    static boolean looksLikeGarbage(String text) {
        int visible = 0;
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            visible++;
            if (Character.isLetterOrDigit(c) || ".,;:()[]\"'-/%€$¿?¡!".indexOf(c) >= 0) letters++;
        }
        return visible >= 20 && letters < visible * 0.5;
    }
}
