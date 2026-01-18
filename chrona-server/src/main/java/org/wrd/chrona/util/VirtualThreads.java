package org.wrd.chrona.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VirtualThreads {
    private static final Logger LOGGER = LogManager.getLogger(VirtualThreads.class);
    private static boolean available = false;

    static {
        try {
            int javaVersion = getJavaVersion();
            available = javaVersion >= 21;

            if (available) {
                LOGGER.info("Virtual threads available (Java {})", javaVersion);
            } else {
                LOGGER.info("Virtual threads not available (Java {} < 21)", javaVersion);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect virtual thread support", e);
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static ExecutorService createExecutor(
        int threads,
        boolean useVirtual,
        @NotNull ThreadFactory fallbackFactory
    ) {
        if (useVirtual && available) {
            try {
                ThreadFactory virtualFactory = (ThreadFactory) Thread.class
                    .getMethod("ofVirtual")
                    .invoke(null);

                return Executors.newThreadPerTaskExecutor(virtualFactory);
            } catch (Exception e) {
                LOGGER.warn("Failed to create virtual thread executor, falling back", e);
            }
        }

        return Executors.newFixedThreadPool(threads, fallbackFactory);
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
