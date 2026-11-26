package exia.js;

import org.teavm.jso.JSBody;

public final class Text {
    private Text() {}

    @JSBody(params = { "a", "b", "strength" }, script = "return a.localeCompare(b, undefined, {sensitivity: strength == 0 ? 'base' : strength == 1 ? 'accent' : 'variant', numeric: false});")
    public static native int compare(String a, String b, int strength);

    @JSBody(params = { "a", "b", "tag", "numeric", "strength" }, script = "return a.localeCompare(b, tag, {sensitivity: strength == 0 ? 'base' : strength == 1 ? 'accent' : 'variant', numeric: numeric});")
    public static native int localeCompare(String a, String b, String tag, boolean numeric, int strength);

    @JSBody(params = { "s" }, script = "return s.normalize('NFD');")
    public static native String decompose(String s);
}
