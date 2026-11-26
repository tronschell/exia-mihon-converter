package exia.js;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Entry point: registers exiaCall(requestJSON, callback) on globalThis. */
public final class Main {
    @JSFunctor
    public interface Reply extends JSObject { void reply(String json); }

    @JSFunctor
    public interface Call extends JSObject { void call(String request, Reply reply); }

    @JSBody(params = { "call" }, script = "globalThis.exiaCall = call;")
    private static native void register(Call call);

    public static void main(String[] args) {
        // Coroutines' stack trace recovery copies exceptions by reflection, which translated code doesn't have.
        System.setProperty("kotlinx.coroutines.debug", "off");
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "false");
        register((request, reply) -> new Thread(() -> reply.reply(exia.runtime.Runner.INSTANCE.handle(request))).start());
    }
}
