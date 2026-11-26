package org.teavm.classlib.java.util.concurrent.locks;

import java.util.WeakHashMap;

/** park/unpark for green threads: each thread waits on its own permit. */
public final class TLockSupport {
    private TLockSupport() {}

    private static final class Permit {
        boolean available;
    }

    private static final java.util.Map<Thread, Permit> permits = new java.util.HashMap<>();

    private static Permit permit(Thread t) {
        Permit p = permits.get(t);
        if (p == null) {
            p = new Permit();
            permits.put(t, p);
        }
        return p;
    }

    public static void park() { parkMillis(0); }
    public static void park(Object blocker) { parkMillis(0); }
    public static void parkNanos(long nanos) { if (nanos > 0) parkMillis(Math.max(1, nanos / 1_000_000)); }
    public static void parkNanos(Object blocker, long nanos) { parkNanos(nanos); }
    public static void parkUntil(long deadline) { parkMillis(Math.max(1, deadline - System.currentTimeMillis())); }
    public static void parkUntil(Object blocker, long deadline) { parkUntil(deadline); }

    private static void parkMillis(long millis) {
        Permit p = permit(Thread.currentThread());
        synchronized (p) {
            if (!p.available) {
                try {
                    if (millis == 0) p.wait(); else p.wait(millis);
                } catch (InterruptedException e) {
                    // returns like a spurious wakeup
                }
            }
            p.available = false;
        }
    }

    public static void unpark(Thread thread) {
        if (thread == null) return;
        Permit p = permit(thread);
        synchronized (p) {
            p.available = true;
            p.notifyAll();
        }
    }

    public static Object getBlocker(Thread t) { return null; }
}
