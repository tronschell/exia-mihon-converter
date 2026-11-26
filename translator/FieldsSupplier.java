import java.util.Collection;
import java.util.List;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;

/** Fields extensions reach by reflection: Keiyoushi's KeiSource replaces HttpSource's lazy headers. */
public class FieldsSupplier implements ReflectionSupplier {
    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        if (className.equals("eu.kanade.tachiyomi.source.online.HttpSource")) return List.of("headers$delegate");
        return List.of();
    }
}
