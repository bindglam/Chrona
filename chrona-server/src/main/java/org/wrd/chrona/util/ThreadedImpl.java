package org.wrd.chrona.util;

class ThreadedImpl<T extends Runnable & AutoCloseable> implements Threaded<T> {
    private final T object;
    private final Thread thread;

    ThreadedImpl(T object) {
        this.object = object;
        this.thread = new Thread(object);
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void stop() throws Exception {
        object.close();

        thread.join();
    }

    @Override
    public T getObject() {
        return object;
    }
}
