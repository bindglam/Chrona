package org.wrd.chrona.tick.phase;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record EntityIntent(
    int entityId,
    IntentType type,
    Vec3 position,
    Vec3 velocity,
    @Nullable Object data,
    String source,
    long priority
) implements Intent {

    public static EntityIntent move(int entityId, Vec3 pos, Vec3 vel, String source, long priority) {
        return new EntityIntent(entityId, IntentType.ENTITY_MOVE, pos, vel, null, source, priority);
    }

    public static EntityIntent spawn(int entityId, Vec3 pos, Object data, String source, long priority) {
        return new EntityIntent(entityId, IntentType.ENTITY_SPAWN, pos, Vec3.ZERO, data, source, priority);
    }

    public static EntityIntent remove(int entityId, String source, long priority) {
        return new EntityIntent(entityId, IntentType.ENTITY_REMOVE, Vec3.ZERO, Vec3.ZERO, null, source, priority);
    }

    public static EntityIntent damage(int entityId, float amount, String source, long priority) {
        return new EntityIntent(entityId, IntentType.DAMAGE, Vec3.ZERO, Vec3.ZERO, amount, source, priority);
    }

    @Override
    public IntentType getType() {
        return type;
    }

    @Override
    public long getPriority() {
        return priority;
    }

    @Override
    public String getSource() {
        return source;
    }
}
