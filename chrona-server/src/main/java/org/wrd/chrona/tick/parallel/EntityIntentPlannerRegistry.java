package org.wrd.chrona.tick.parallel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EntityIntentPlannerRegistry {
    private static final List<EntityIntentComputer> COMPUTERS = new CopyOnWriteArrayList<>();

    private EntityIntentPlannerRegistry() {
    }

    public static void register(EntityIntentComputer computer) {
        if (computer == null) {
            return;
        }
        COMPUTERS.add(computer);
    }

    public static List<EntityIntentComputer> snapshot() {
        return COMPUTERS.isEmpty() ? List.of() : List.copyOf(COMPUTERS);
    }

    public static int getComputerCount() {
        return COMPUTERS.size();
    }

    public static void clear() {
        COMPUTERS.clear();
    }
}
