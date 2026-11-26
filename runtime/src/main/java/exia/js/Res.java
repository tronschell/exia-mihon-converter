package exia.js;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/** Class.getResource for translated extensions: the translator rewrites those calls to [getResource]. */
public final class Res {
    private Res() {}

    public static URL getResource(Class<?> type, String name) {
        String path = name.startsWith("/") ? name.substring(1) : name;
        InputStream probe = type.getClassLoader().getResourceAsStream(path);
        if (probe == null) return null;
        try {
            return new URL("exia-res", "", -1, "/" + path, new Handler(type, path));
        } catch (MalformedURLException e) {
            return null;
        }
    }

    static final class Handler extends URLStreamHandler {
        private final Class<?> type;
        private final String path;
        Handler(Class<?> type, String path) { this.type = type; this.path = path; }
        @Override
        protected URLConnection openConnection(URL u) {
            return new URLConnection(u) {
                @Override public void connect() {}
                @Override public InputStream getInputStream() throws IOException {
                    InputStream s = type.getClassLoader().getResourceAsStream(path);
                    if (s == null) throw new IOException("missing resource " + path);
                    return s;
                }
            };
        }
    }

    static InputStream bytes(byte[] b) { return new ByteArrayInputStream(b); }
}
