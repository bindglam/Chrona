package org.wrd.chrona.tick.phase;

public class IntentPriority {
    public static final long VANILLA = 1000L;
    public static final long PLUGIN = 500L;
    public static final long ENTITY_AI = 800L;
    public static final long REDSTONE = 700L;
    public static final long PHYSICS = 900L;
    public static final long SYSTEM = 2000L;

    private IntentPriority() {}

    public static long withTimestamp(long basePriority) {
        return basePriority * 1_000_000_000L + System.nanoTime();
    }

    public static long fromSource(String source) {
        return switch (source) {
            case "vanilla", "system" -> SYSTEM;
            case "entity_ai" -> ENTITY_AI;
            case "redstone" -> REDSTONE;
            case "physics" -> PHYSICS;
            case "plugin" -> PLUGIN;
            default -> PLUGIN;
        };
    }
}
