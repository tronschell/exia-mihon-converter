package org.teavm.classlib.java.net;

import org.teavm.jso.JSBody;

public final class TIDN {
    public static final int ALLOW_UNASSIGNED = 1;
    public static final int USE_STD3_ASCII_RULES = 2;
    private TIDN() {}

    public static String toASCII(String input) { return toASCII(input, 0); }
    public static String toASCII(String input, int flag) {
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) > 127) return punycode(input);
        }
        return input;
    }
    public static String toUnicode(String input) { return input; }
    public static String toUnicode(String input, int flag) { return input; }

    @JSBody(params = "host", script = "try { return new URL('http://' + host).hostname; } catch (e) { return host; }")
    private static native String punycode(String host);
}
