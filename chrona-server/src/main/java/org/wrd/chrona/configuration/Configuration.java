package org.wrd.chrona.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class Configuration {
    private final File configFile;

    private final Set<Field<?>> fields = new HashSet<>();

    private YamlConfiguration config;

    public Configuration(File configFile) {
        this.configFile = configFile;
    }

    public void load() throws IOException {
        if(!configFile.exists()) {
            configFile.createNewFile();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        fields.forEach(Field::load);

        config.save(configFile);
    }

    protected <T> Field<T> createField(String path, @Nullable T defaultValue) {
        Field<T> field = new Field<>(path, defaultValue, this);
        fields.add(field);
        return field;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
