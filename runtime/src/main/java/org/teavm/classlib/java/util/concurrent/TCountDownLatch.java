package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

public class TCountDownLatch {
    private final Object monitor = new Object();
    private long count;

    public TCountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException();
        this.count = count;
    }

    public void await() throws InterruptedException {
        synchronized (monitor) { while (count > 0) monitor.wait(); }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (monitor) {
            while (count > 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return false;
                monitor.wait(left);
            }
            return true;
        }
    }

    public void countDown() {
        synchronized (monitor) {
            if (count > 0 && --count == 0) monitor.notifyAll();
        }
    }

    public long getCount() { synchronized (monitor) { return count; } }
}
