package org.wrd.chrona.http;

public record ServerProperties(int port) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int port = 1920;

        private Builder() {
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public ServerProperties build() {
            return new ServerProperties(port);
        }
    }
}
