package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.WorldSnapshot;

import java.util.List;

public final class EntityIntentPipeline {
    private static final Logger LOGGER = LogManager.getLogger(EntityIntentPipeline.class);
    private final List<EntityIntentComputer> computers;

    public EntityIntentPipeline(List<EntityIntentComputer> computers) {
        this.computers = List.copyOf(computers);
    }

    public boolean isEmpty() {
        return computers.isEmpty();
    }

    public void compute(WorldSnapshot snapshot, Entity entity, IntentMerger intentMerger, long tickId) {
        WorldSnapshot.EntitySnapshot entitySnapshot = snapshot.getEntity(entity.getId());
        if (entitySnapshot == null) {
            return;
        }

        for (EntityIntentComputer computer : computers) {
            if (!computer.supports(entity, entitySnapshot)) {
                continue;
            }
            try {
                computer.compute(snapshot, entity, entitySnapshot, intentMerger, tickId);
            } catch (Exception e) {
                LOGGER.warn("Entity intent compute failed for entity {}", entity.getId(), e);
            }
        }
    }
}
