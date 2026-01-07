package org.wrd.chrona.configuration;

import org.jetbrains.annotations.Nullable;

public final class Field<T> {
    private final String path;
    private final @Nullable T defaultValue;
    private final Configuration configuration;

    private @Nullable T value;

    public Field(String path, @Nullable T defaultValue, Configuration configuration) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.configuration = configuration;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        value = (T) configuration.getConfig().get(path);

        if(value == null && defaultValue != null) {
            value = defaultValue;

            configuration.getConfig().set(path, defaultValue);
        }
    }

    public String getPath() {
        return path;
    }

    public @Nullable T getDefaultValue() {
        return defaultValue;
    }

    public @Nullable T getValue() {
        if(value == null)
            load();

        return value;
    }
}
