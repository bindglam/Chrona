package org.wrd.chrona.util.lockfree;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MpmcQueue<T> {
    private final AtomicReferenceArray<T> buffer;
    private final int capacity;
    private final int mask;
    private final AtomicLong head = new AtomicLong(0);
    private final AtomicLong tail = new AtomicLong(0);

    public MpmcQueue(int capacity) {
        this.capacity = nextPowerOfTwo(capacity);
        this.mask = this.capacity - 1;
        this.buffer = new AtomicReferenceArray<>(this.capacity);
    }

    public boolean offer(T item) {
        if (item == null) throw new NullPointerException();

        long currentTail;
        long newTail;
        do {
            currentTail = tail.get();
            long currentHead = head.get();

            if (currentTail - currentHead >= capacity) {
                return false;
            }

            newTail = currentTail + 1;
        } while (!tail.compareAndSet(currentTail, newTail));

        int index = (int) (currentTail & mask);
        buffer.set(index, item);
        return true;
    }

    public T poll() {
        long currentHead;
        long newHead = 0;
        T item;

        do {
            currentHead = head.get();
            long currentTail = tail.get();

            if (currentHead >= currentTail) {
                return null;
            }

            int index = (int) (currentHead & mask);
            item = buffer.get(index);

            if (item == null) {
                Thread.yield();
                continue;
            }

            newHead = currentHead + 1;
        } while (!head.compareAndSet(currentHead, newHead));

        int index = (int) (currentHead & mask);
        buffer.set(index, null);
        return item;
    }

    public int size() {
        return (int) (tail.get() - head.get());
    }

    public boolean isEmpty() {
        return head.get() >= tail.get();
    }

    private static int nextPowerOfTwo(int n) {
        n--;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }
}
