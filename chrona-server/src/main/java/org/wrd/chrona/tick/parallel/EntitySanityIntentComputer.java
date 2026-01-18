package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.phase.EntityIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.WorldSnapshot;

public class EntitySanityIntentComputer implements EntityIntentComputer {
    @Override
    public boolean supports(Entity entity, WorldSnapshot.EntitySnapshot snapshot) {
        return ChronaConfig.entitySanityCheck && !(entity instanceof Player);
    }

    @Override
    public void compute(WorldSnapshot snapshot, Entity entity, WorldSnapshot.EntitySnapshot entitySnapshot,
                        IntentMerger intentMerger, long tickId) {
        Vec3 pos = entitySnapshot.position();
        Vec3 vel = entitySnapshot.velocity();

        if (!isFinite(pos)) {
            intentMerger.submit(EntityIntent.remove(entitySnapshot.id(), "sanity", System.nanoTime()), tickId);
            return;
        }

        if (!isFinite(vel)) {
            intentMerger.submit(EntityIntent.move(entitySnapshot.id(), pos, Vec3.ZERO, "sanity", System.nanoTime()), tickId);
        }
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
