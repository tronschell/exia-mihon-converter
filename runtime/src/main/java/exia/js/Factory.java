package exia.js;

import java.util.HashMap;
import java.util.Map;

/** Replaced per extension by the translator: creates the extension's sources and lists its Injekt types. */
public final class Factory {
    public static final Map<String, String> types = new HashMap<>();
    public static Object create() { return null; }
    /** Services only some extensions use (ProtoBuf), so the others don't carry them. */
    public static Object provide(String type) { return null; }
}
