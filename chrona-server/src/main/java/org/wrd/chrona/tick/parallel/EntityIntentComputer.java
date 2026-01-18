package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.WorldSnapshot;

public interface EntityIntentComputer {
    boolean supports(Entity entity, WorldSnapshot.EntitySnapshot snapshot);

    void compute(WorldSnapshot snapshot, Entity entity, WorldSnapshot.EntitySnapshot entitySnapshot,
                 IntentMerger intentMerger, long tickId);
}
