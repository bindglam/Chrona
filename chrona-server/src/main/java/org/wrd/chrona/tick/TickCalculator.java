package org.wrd.chrona.tick;

public final class TickCalculator {
    private static long lastTick = System.currentTimeMillis();
    private static long currentTick = System.currentTimeMillis();
    private static double allMissedTicks = 0.0;

    private TickCalculator() {
    }

    public static void tick() {
        lastTick = currentTick;
        currentTick = System.currentTimeMillis();

        allMissedTicks -= Math.floor(allMissedTicks);

        long mspt = currentTick - lastTick <= 0 ? 50 : currentTick - lastTick;
        double missedTicks = (mspt / 50.0) - 1;
        allMissedTicks += missedTicks <= 0 ? 0 : missedTicks;
    }

    public static double getAllMissedTicks() {
        return allMissedTicks;
    }

    public static int getMissedTicks() {
        return (int) Math.floor(allMissedTicks);
    }
}
