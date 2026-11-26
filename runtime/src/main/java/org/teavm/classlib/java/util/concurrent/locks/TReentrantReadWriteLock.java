package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/** Read and write share one reentrant lock: correct, just without concurrent readers (JavaScript runs one thread). */
public class TReentrantReadWriteLock implements TReadWriteLock, java.io.Serializable {
    private final TReentrantLock lock = new TReentrantLock();
    private final ReadLock readLock = new ReadLock(this);
    private final WriteLock writeLock = new WriteLock(this);

    public TReentrantReadWriteLock() {}
    public TReentrantReadWriteLock(boolean fair) {}

    @Override public ReadLock readLock() { return readLock; }
    @Override public WriteLock writeLock() { return writeLock; }
    public int getReadHoldCount() { return lock.getHoldCount(); }
    public int getWriteHoldCount() { return lock.getHoldCount(); }
    public boolean isWriteLocked() { return lock.isLocked(); }

    public static class ReadLock implements TLock, java.io.Serializable {
        private final TReentrantLock lock;
        protected ReadLock(TReentrantReadWriteLock owner) { lock = owner.lock; }
        @Override public void lock() { lock.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { lock.lockInterruptibly(); }
        @Override public boolean tryLock() { return lock.tryLock(); }
        @Override public boolean tryLock(long t, TimeUnit u) throws InterruptedException { return lock.tryLock(t, u); }
        @Override public void unlock() { lock.unlock(); }
        @Override public TCondition newCondition() { throw new UnsupportedOperationException(); }
    }

    public static class WriteLock implements TLock, java.io.Serializable {
        private final TReentrantLock lock;
        protected WriteLock(TReentrantReadWriteLock owner) { lock = owner.lock; }
        @Override public void lock() { lock.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { lock.lockInterruptibly(); }
        @Override public boolean tryLock() { return lock.tryLock(); }
        @Override public boolean tryLock(long t, TimeUnit u) throws InterruptedException { return lock.tryLock(t, u); }
        @Override public void unlock() { lock.unlock(); }
        @Override public TCondition newCondition() { return lock.newCondition(); }
        public boolean isHeldByCurrentThread() { return lock.isHeldByCurrentThread(); }
    }
}
