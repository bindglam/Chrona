package org.wrd.chrona.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.wrd.chrona.tick.phase.BlockIntent;
import org.wrd.chrona.tick.phase.Intent;
import org.wrd.chrona.tick.phase.IntentMerger;

import java.util.List;

public class IntentTest {

    public static void main(String[] args) {
        testIntentMergeConflict();
        testIntentPriority();
    }

    private static void testIntentMergeConflict() {
        IntentMerger merger = new IntentMerger();
        long tickId = 1;
        BlockPos pos = new BlockPos(0, 64, 0);

        merger.beginTick(tickId);
        merger.submit(BlockIntent.set(pos, Blocks.STONE.defaultBlockState(), "plugin1", 100));
        merger.submit(BlockIntent.set(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), "plugin2", 200));
        merger.submit(BlockIntent.set(pos, Blocks.GOLD_BLOCK.defaultBlockState(), "vanilla", 300));
        merger.endTick(tickId);

        List<Intent> merged = merger.merge();

        assert merged.size() == 1;
        BlockIntent result = (BlockIntent) merged.get(0);
        assert result.priority() == 300;
        assert result.source().equals("vanilla");

        System.out.println("✓ Intent conflict resolution works (highest priority wins)");
    }

    private static void testIntentPriority() {
        IntentMerger merger = new IntentMerger();
        long tickId = 1;

        BlockPos pos1 = new BlockPos(0, 64, 0);
        BlockPos pos2 = new BlockPos(1, 64, 0);

        merger.beginTick(tickId);
        merger.submit(BlockIntent.set(pos1, Blocks.STONE.defaultBlockState(), "low", 100));
        merger.submit(BlockIntent.set(pos2, Blocks.DIAMOND_BLOCK.defaultBlockState(), "high", 500));
        merger.endTick(tickId);

        List<Intent> merged = merger.merge();

        assert merged.size() == 2;
        assert merged.get(0).getPriority() == 500;
        assert merged.get(1).getPriority() == 100;

        System.out.println("✓ Intent priority ordering works");
    }
}
