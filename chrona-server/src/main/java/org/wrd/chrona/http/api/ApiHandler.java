package org.wrd.chrona.http.api;

public interface ApiHandler {
    default void get(HandlerContext context) {
    }

    default void post(HandlerContext context) {
    }
}
