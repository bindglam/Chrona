package org.wrd.chrona.util;

public interface Threaded<T extends Runnable & AutoCloseable> {
    void start();

    void stop() throws Exception;

    T getObject();

    static <T extends Runnable & AutoCloseable> Threaded<T> wrap(T object) {
        return new ThreadedImpl<>(object);
    }
}
