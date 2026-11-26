package exia.js;

import java.io.IOException;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/** What the app provides to a translated extension: network, settings, logging and a clock. */
public final class Host {
    private Host() {}

    /** One HTTP exchange through the app. [meta] is JSON {method,url,headers:[[k,v]...]}; returns {meta JSON, body}. */
    @Async
    public static native Object[] fetch(String meta, byte[] body) throws IOException;

    private static void fetch(String meta, byte[] body, AsyncCallback<Object[]> callback) {
        fetchJS(meta, body == null ? null : Int8Array.copyFromJavaArray(body), (responseMeta, responseBody, error) -> {
            if (error != null) callback.error(new IOException(error));
            else callback.complete(new Object[] { responseMeta, responseBody == null ? new byte[0] : responseBody.copyToJavaArray() });
        });
    }

    @JSFunctor
    public interface FetchDone extends JSObject {
        void done(String meta, Int8Array body, String error);
    }

    @JSBody(params = { "meta", "body", "done" }, script = "exia.fetch(meta, body, done);")
    private static native void fetchJS(String meta, Int8Array body, FetchDone done);

    /** Asks the app to pass a website check (Cloudflare) for url in a web view; true once its cookies are set. */
    @Async
    public static native boolean challenge(String url, String userAgent);

    private static void challenge(String url, String userAgent, AsyncCallback<Boolean> callback) {
        challengeJS(url, userAgent, ok -> callback.complete(ok));
    }

    @JSFunctor
    public interface ChallengeDone extends JSObject { void done(boolean ok); }

    @JSBody(params = { "url", "ua", "done" }, script = "if (exia.challenge) exia.challenge(url, ua, done); else done(false);")
    private static native void challengeJS(String url, String userAgent, ChallengeDone done);

    @Async
    public static native void sleep(double millis);

    private static void sleep(double millis, AsyncCallback<Void> callback) {
        later(millis, () -> callback.complete(null));
    }

    @JSFunctor
    public interface Task extends JSObject { void run(); }

    @JSBody(params = { "ms", "task" }, script = "setTimeout(task, ms);")
    private static native void later(double ms, Task task);

    @JSBody(params = { "key" }, script = "var v = exia.prefGet(key); return v === undefined ? null : v;")
    public static native String prefGet(String key);

    @JSBody(params = { "key", "value" }, script = "exia.prefSet(key, value);")
    public static native void prefSet(String key, String value);

    @JSBody(params = { "level", "message" }, script = "exia.log(level, message);")
    public static native void log(int level, String message);

    /** The JavaScript stack where a Java exception was created (for logs). */
    public static String jsStack(Throwable e) { return jsStackOf(e); }

    @JSBody(params = { "e" }, script = "var x = e && (e.$jsException || e.$jsError); return x && x.stack ? String(x.stack) : String(new Error().stack);")
    private static native String jsStackOf(Object e);

    /** Hands bytes to the app without encoding them; returns the name a reply refers to them by. */
    public static String deliver(byte[] bytes) { return deliverJS(Int8Array.copyFromJavaArray(bytes)); }

    @JSBody(params = { "bytes" }, script = "return exia.deliver(bytes);")
    private static native String deliverJS(Int8Array bytes);

    @JSBody(script = "return Date.now();")
    public static native double now();

    @JSBody(params = { "name" }, script = "return exia.resource ? exia.resource(name) : null;")
    public static native Int8Array resourceJS(String name);

    public static byte[] resource(String name) {
        Int8Array a = resourceJS(name);
        return a == null ? null : a.copyToJavaArray();
    }

    /** Crypto through the app (WebCrypto-like, synchronous): op, key, iv, data, extra JSON. */
    @JSBody(params = { "op", "transformation", "key", "iv", "data", "extra" },
            script = "return exia.crypto(op, transformation, key, iv, data, extra);")
    public static native Int8Array cryptoJS(String op, String transformation, Int8Array key, Int8Array iv, Int8Array data, String extra);

    public static byte[] crypto(String op, String transformation, byte[] key, byte[] iv, byte[] data, String extra) {
        Int8Array r = cryptoJS(op, transformation, key == null ? null : Int8Array.copyFromJavaArray(key),
            iv == null ? null : Int8Array.copyFromJavaArray(iv), data == null ? null : Int8Array.copyFromJavaArray(data), extra);
        if (r == null) throw new IllegalStateException("crypto failed: " + transformation);
        return r.copyToJavaArray();
    }

    /** Runs a site's script (QuickJS in Mihon) in a fresh global scope; returns JSON {value} or {error}. */
    @JSBody(params = { "script" }, script = "return exia.evaluate(script);")
    public static native String evaluate(String script);

    /** The app's cookie store (shared with the web view): Cookie header value for url, or null. */
    @JSBody(params = { "url" }, script = "var v = exia.cookieGet(url); return v === undefined ? null : v;")
    public static native String cookieGet(String url);

    @JSBody(params = { "url", "cookie" }, script = "exia.cookieSet(url, cookie);")
    public static native void cookieSet(String url, String cookie);

    /** Image work through the app (decode, create, draw, fill, text, encode, read, write, free). Returns a short text result. */
    @JSBody(params = { "op", "args", "data" }, script = "var r = exia.image(op, args, data); return r == null ? null : String(r);")
    private static native String imageJS(String op, String args, Int8Array data);

    @JSBody(params = { "op", "args", "data" }, script = "return exia.imageBytes(op, args, data);")
    private static native Int8Array imageBytesJS(String op, String args, Int8Array data);

    public static String image(String op, String args, byte[] data) {
        return imageJS(op, args, data == null ? null : Int8Array.copyFromJavaArray(data));
    }

    public static byte[] imageBytes(String op, String args, byte[] data) {
        Int8Array r = imageBytesJS(op, args, data == null ? null : Int8Array.copyFromJavaArray(data));
        return r == null ? null : r.copyToJavaArray();
    }
}
