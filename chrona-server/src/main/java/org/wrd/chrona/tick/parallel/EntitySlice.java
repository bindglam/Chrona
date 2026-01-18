package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;

public record EntitySlice(Entity[] entities, int start, int end) {

    public int size() {
        return end - start;
    }

    public static EntitySlice[] chunks(Entity[] entities, int chunkSize) {
        int slices = (entities.length + chunkSize - 1) / chunkSize;
        EntitySlice[] result = new EntitySlice[slices];

        for (int i = 0; i < slices; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, entities.length);
            result[i] = new EntitySlice(entities, start, end);
        }

        return result;
    }

    public static EntitySlice[] splitEvenly(Entity[] entities, int parts) {
        int sliceSize = (entities.length + parts - 1) / parts;
        return chunks(entities, sliceSize);
    }
}
