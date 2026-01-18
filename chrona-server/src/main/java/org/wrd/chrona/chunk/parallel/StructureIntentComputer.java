package org.wrd.chrona.chunk.parallel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.chunk.WorldGenContext;
import org.wrd.chrona.tick.phase.ChunkIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.IntentPriority;

import java.util.Random;

/**
 * Structure placement intent computer
 * Determines structure placement and generates STRUCTURE_PLACE intents
 */
public class StructureIntentComputer implements ChunkIntentComputer {
    private static final Logger LOGGER = LogManager.getLogger(StructureIntentComputer.class);

    // Structure spawn rates (simplified)
    private static final double VILLAGE_CHANCE = 0.002;
    private static final double MINESHAFT_CHANCE = 0.005;
    private static final double DUNGEON_CHANCE = 0.01;

    @Override
    public boolean supports(int chunkX, int chunkZ, WorldGenContext context) {
        // Support all chunks for structure checks
        return true;
    }

    @Override
    public void compute(WorldGenContext context, int chunkX, int chunkZ, IntentMerger merger, long tickId) {
        try {
            long seed = context.getSeed();
            Random random = new Random(seed ^ ((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL)) ^ 0x12345678L);

            // Check for various structures
            checkAndPlaceStructure(random, chunkX, chunkZ, "village", VILLAGE_CHANCE, merger, tickId);
            checkAndPlaceStructure(random, chunkX, chunkZ, "mineshaft", MINESHAFT_CHANCE, merger, tickId);
            checkAndPlaceStructure(random, chunkX, chunkZ, "dungeon", DUNGEON_CHANCE, merger, tickId);

        } catch (Exception e) {
            LOGGER.warn("Error computing structures for chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
        }
    }

    private void checkAndPlaceStructure(
            Random random,
            int chunkX,
            int chunkZ,
            String structureId,
            double chance,
            IntentMerger merger,
            long tickId) {

        if (random.nextDouble() >= chance) {
            return;
        }

        // Calculate structure position
        int worldX = chunkX * 16 + random.nextInt(16);
        int worldZ = chunkZ * 16 + random.nextInt(16);
        int worldY = getStructureBaseY(structureId);

        // Get structure size
        int[] size = getStructureSize(structureId);

        // Create structure data
        ChunkIntent.StructureData structureData = new ChunkIntent.StructureData(
            structureId,
            worldX, worldY, worldZ,
            size[0], size[1], size[2],
            new byte[0], // Placeholder - would contain actual block data
            true // requiresGround
        );

        // Submit structure intent
        merger.submit(
            ChunkIntent.structure(
                chunkX,
                chunkZ,
                structureData,
                "structure_" + structureId,
                IntentPriority.withTimestamp(IntentPriority.VANILLA - 100) // Lower priority than terrain
            ),
            tickId
        );

        LOGGER.debug("Generated structure intent for {} at {},{},{}", structureId, worldX, worldY, worldZ);
    }

    private int getStructureBaseY(String structureId) {
        return switch (structureId) {
            case "village" -> 64;
            case "mineshaft" -> 30;
            case "dungeon" -> 40;
            default -> 64;
        };
    }

    private int[] getStructureSize(String structureId) {
        return switch (structureId) {
            case "village" -> new int[]{32, 16, 32};
            case "mineshaft" -> new int[]{64, 10, 64};
            case "dungeon" -> new int[]{7, 5, 7};
            default -> new int[]{16, 16, 16};
        };
    }

    @Override
    public int getPriority() {
        return 50; // Run after noise generation
    }

    @Override
    public String getName() {
        return "StructureIntentComputer";
    }
}
