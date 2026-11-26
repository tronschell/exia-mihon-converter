package okhttp3.internal.publicsuffix;

import okio.Buffer;
import okio.FileSystem;
import okio.Path;
import okio.Source;

/** Exia: the public suffix list comes from the app, one copy for every extension. */
public final class ResourcePublicSuffixList extends BasePublicSuffixList {
    public static final Path PUBLIC_SUFFIX_RESOURCE = Path.get("/okhttp3/internal/publicsuffix/PublicSuffixDatabase.list");

    public ResourcePublicSuffixList() {}
    public ResourcePublicSuffixList(Path path, FileSystem fileSystem) {}
    public ResourcePublicSuffixList(Path path, FileSystem fileSystem, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {}

    @Override
    public Source listSource() {
        byte[] bytes = exia.js.Host.resource("okhttp3/internal/publicsuffix/PublicSuffixDatabase.list");
        if (bytes == null) throw new IllegalStateException("No public suffix list");
        return new Buffer().write(bytes);
    }

    @Override
    public Object getPath() { return PUBLIC_SUFFIX_RESOURCE; }
}
