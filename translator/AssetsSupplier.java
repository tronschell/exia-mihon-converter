import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/** Embeds the extension's own assets (assets/i18n/*.properties and the like) in its JavaScript. */
public class AssetsSupplier implements ResourceSupplier {
    static volatile String[] current = new String[0];
    @Override
    public String[] supplyResources(ResourceSupplierContext context) { return current; }
}
