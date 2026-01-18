package org.wrd.chrona.simd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SIMDDetection {
    private static final Logger LOGGER = LogManager.getLogger(SIMDDetection.class);
    private static boolean enabled = false;
    private static int vectorBitSize = 0;

    public static void init() {
        try {
            int javaVersion = getJavaVersion();
            if (javaVersion < 17) {
                LOGGER.info("SIMD disabled: Java 17+ required (current: {})", javaVersion);
                return;
            }

            Class.forName("jdk.incubator.vector.VectorSpecies");

            Class<?> intVectorClass = Class.forName("jdk.incubator.vector.IntVector");
            Object species = intVectorClass.getField("SPECIES_PREFERRED").get(null);

            vectorBitSize = (int) species.getClass().getMethod("vectorBitSize").invoke(species);
            enabled = vectorBitSize >= 128;

            if (enabled) {
                LOGGER.info("SIMD enabled: {} bit vectors", vectorBitSize);
            } else {
                LOGGER.info("SIMD disabled: vector size too small");
            }
        } catch (Exception e) {
            LOGGER.info("SIMD not available: {}", e.getMessage());
            enabled = false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getVectorBitSize() {
        return vectorBitSize;
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }
}
