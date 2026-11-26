package org.teavm.classlib.java.text;

/** Identity normalization: hosts and text from sites are already composed in practice. */
public final class TNormalizer {
    private TNormalizer() {}
    public enum Form { NFD, NFC, NFKD, NFKC }
    public static String normalize(CharSequence src, Form form) {
        String s = src.toString();
        if (form == Form.NFD || form == Form.NFKD) return exia.js.Text.decompose(s);
        return s;
    }
    public static boolean isNormalized(CharSequence src, Form form) { return true; }
}
