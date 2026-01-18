package org.wrd.chrona.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;

/**
 * Immutable result of parallel chunk generation
 * Contains all data needed to commit a chunk to the world
 */
public class ChunkGenResult {
    private final int chunkX;
    private final int chunkZ;
    private final LevelChunkSection[] sections;
    private final Map<Heightmap.Types, int[]> heightmaps;
    private final StructureIntent[] structureIntents;
    private final long generationTime;

    public ChunkGenResult(int chunkX, int chunkZ, LevelChunkSection[] sections,
                          Map<Heightmap.Types, int[]> heightmaps,
                          StructureIntent[] structureIntents,
                          long generationTime) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = sections;
        this.heightmaps = heightmaps;
        this.structureIntents = structureIntents;
        this.generationTime = generationTime;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public LevelChunkSection[] getSections() {
        return sections;
    }

    public Map<Heightmap.Types, int[]> getHeightmaps() {
        return heightmaps;
    }

    public StructureIntent[] getStructureIntents() {
        return structureIntents;
    }

    public long getGenerationTime() {
        return generationTime;
    }

    /**
     * Structure placement intent for commit phase
     */
    public static class StructureIntent {
        private final BlockPos pos;
        private final BlockState state;
        private final String structureType;
        private final long priority;

        public StructureIntent(BlockPos pos, BlockState state, String structureType, long priority) {
            this.pos = pos;
            this.state = state;
            this.structureType = structureType;
            this.priority = priority;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockState getState() {
            return state;
        }

        public String getStructureType() {
            return structureType;
        }

        public long getPriority() {
            return priority;
        }
    }
}
