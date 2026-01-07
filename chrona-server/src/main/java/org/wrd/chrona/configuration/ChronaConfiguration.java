package org.wrd.chrona.configuration;

import java.io.File;

public final class ChronaConfiguration extends Configuration {
    private static final int CONFIG_VERSION = 100;
    private static final File CONFIG_FILE = new File("chrona.yml");

    public final Field<Boolean> minimizeTPSLag = createField("optimization.minimize-tps-lag", false);

    public ChronaConfiguration() {
        super(CONFIG_FILE);
    }
}
