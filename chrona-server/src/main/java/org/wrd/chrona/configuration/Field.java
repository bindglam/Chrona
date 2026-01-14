package org.wrd.chrona.configuration;

import org.jetbrains.annotations.NotNull;

public final class Field<T> {
    private final String path;
    private final @NotNull T defaultValue;
    private final Configuration configuration;

    private T value;

    public Field(String path, @NotNull T defaultValue, Configuration configuration) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.configuration = configuration;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        value = (T) configuration.getConfig().get(path);

        if(value == null) {
            value = defaultValue;

            configuration.getConfig().set(path, defaultValue);
        }
    }

    public String getPath() {
        return path;
    }

    public @NotNull T getDefaultValue() {
        return defaultValue;
    }

    public @NotNull T getValue() {
        if(value == null)
            load();

        return value;
    }
}
