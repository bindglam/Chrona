package org.wrd.chrona.tick.phase;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * Intent for chunk generation operations
 * Used for both terrain generation and structure placement
 */
public record ChunkIntent(
    int chunkX,
    int chunkZ,
    IntentType type,
    @Nullable ChunkData chunkData,
    @Nullable StructureData structureData,
    String source,
    long priority
) implements Intent {

    /**
     * Create a chunk generation intent
     */
    public static ChunkIntent generate(int chunkX, int chunkZ, ChunkData data, String source, long priority) {
        return new ChunkIntent(chunkX, chunkZ, IntentType.CHUNK_GEN, data, null, source, priority);
    }

    /**
     * Create a structure placement intent
     */
    public static ChunkIntent structure(int chunkX, int chunkZ, StructureData data, String source, long priority) {
        return new ChunkIntent(chunkX, chunkZ, IntentType.STRUCTURE_PLACE, null, data, source, priority);
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

    /**
     * Get the packed chunk position
     */
    public long packedPos() {
        return net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
    }

    /**
     * Data for chunk terrain generation
     */
    public record ChunkData(
        LevelChunkSection[] sections,
        int[] heightmap,
        byte[] biomeData,
        long generationTimeNanos
    ) {
        public static ChunkData empty() {
            return new ChunkData(new LevelChunkSection[0], new int[0], new byte[0], 0);
        }

        public boolean isEmpty() {
            return sections == null || sections.length == 0;
        }
    }

    /**
     * Data for structure placement
     */
    public record StructureData(
        String structureId,
        int startX, int startY, int startZ,
        int sizeX, int sizeY, int sizeZ,
        byte[] blockData,
        boolean requiresGround
    ) {
        public static StructureData empty() {
            return new StructureData("", 0, 0, 0, 0, 0, 0, new byte[0], false);
        }

        public boolean isEmpty() {
            return structureId == null || structureId.isEmpty() || blockData == null || blockData.length == 0;
        }
    }
}
