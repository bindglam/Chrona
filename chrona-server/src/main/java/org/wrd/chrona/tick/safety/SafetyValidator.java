package org.wrd.chrona.tick.safety;

import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SafetyValidator {
    private static final Logger LOGGER = LogManager.getLogger(SafetyValidator.class);
    private static final ConcurrentHashMap<String, AtomicInteger> violations = new ConcurrentHashMap<>();

    public static boolean validateBlockAccess(BlockPos pos, String accessor) {
        // Validate block is within world bounds
        if (pos.getY() < -64 || pos.getY() > 320) {
            recordViolation("out_of_bounds", accessor);
            return false;
        }
        return true;
    }

    public static boolean validateThreadAccess(String operation) {
        Thread current = Thread.currentThread();
        String name = current.getName();

        if (name.startsWith("Chrona-Compute") && operation.contains("write")) {
            recordViolation("compute_thread_write", operation);
            LOGGER.warn("Compute thread {} attempted write operation: {}", name, operation);
            return false;
        }

        return true;
    }

    private static void recordViolation(String type, String detail) {
        AtomicInteger counter = violations.computeIfAbsent(type, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count % 100 == 1) {
            LOGGER.warn("Safety violation {}: {} (count: {})", type, detail, count);
        }
    }

    public static void printViolations() {
        if (violations.isEmpty()) {
            LOGGER.info("No safety violations detected");
            return;
        }

        LOGGER.warn("=== Safety Violations ===");
        violations.forEach((type, count) ->
            LOGGER.warn("  {}: {} times", type, count.get())
        );
    }
}
