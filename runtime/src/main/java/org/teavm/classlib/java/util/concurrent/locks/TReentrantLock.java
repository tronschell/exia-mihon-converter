package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/** A reentrant lock for TeaVM's green threads, on an object monitor (wait/notify suspend the green thread). */
public class TReentrantLock implements TLock, java.io.Serializable {
    private final Object monitor = new Object();
    private Thread owner;
    private int holds;

    public TReentrantLock() {}
    public TReentrantLock(boolean fair) {}

    @Override
    public void lock() {
        Thread me = Thread.currentThread();
        synchronized (monitor) {
            while (owner != null && owner != me) {
                try { monitor.wait(); } catch (InterruptedException e) { /* keep waiting */ }
            }
            owner = me;
            holds++;
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException { lock(); }

    @Override
    public boolean tryLock() {
        Thread me = Thread.currentThread();
        synchronized (monitor) {
            if (owner != null && owner != me) return false;
            owner = me;
            holds++;
            return true;
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        Thread me = Thread.currentThread();
        synchronized (monitor) {
            while (owner != null && owner != me) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return false;
                monitor.wait(left);
            }
            owner = me;
            holds++;
            return true;
        }
    }

    @Override
    public void unlock() {
        synchronized (monitor) {
            if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
            if (--holds == 0) {
                owner = null;
                monitor.notifyAll();
            }
        }
    }

    public boolean isLocked() { synchronized (monitor) { return owner != null; } }
    public boolean isHeldByCurrentThread() { synchronized (monitor) { return owner == Thread.currentThread(); } }
    public int getHoldCount() { synchronized (monitor) { return owner == Thread.currentThread() ? holds : 0; } }

    @Override
    public TCondition newCondition() { return new ConditionImpl(); }

    private final class ConditionImpl implements TCondition {
        private final Object signals = new Object();

        private int release() {
            synchronized (monitor) {
                int saved = holds;
                holds = 0;
                owner = null;
                monitor.notifyAll();
                return saved;
            }
        }

        private void reacquire(int saved) {
            lock();
            synchronized (monitor) { holds = saved; }
        }

        @Override
        public void await() throws InterruptedException {
            synchronized (signals) {
                int saved = release();
                try { signals.wait(); } finally { reacquire(saved); }
            }
        }

        @Override
        public void awaitUninterruptibly() {
            try { await(); } catch (InterruptedException e) { /* ignore */ }
        }

        @Override
        public long awaitNanos(long nanos) throws InterruptedException {
            long start = System.nanoTime();
            synchronized (signals) {
                int saved = release();
                try { signals.wait(Math.max(1, nanos / 1_000_000)); } finally { reacquire(saved); }
            }
            return nanos - (System.nanoTime() - start);
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitNanos(unit.toNanos(time)) > 0;
        }

        @Override
        public void signal() { synchronized (signals) { signals.notify(); } }

        @Override
        public void signalAll() { synchronized (signals) { signals.notifyAll(); } }
    }
}
