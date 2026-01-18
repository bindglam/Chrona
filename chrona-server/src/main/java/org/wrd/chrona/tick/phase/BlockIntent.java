package org.wrd.chrona.tick.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BlockIntent(
    BlockPos pos,
    BlockState newState,
    String source,
    long priority,
    IntentType type
) implements Intent {

    public static BlockIntent set(BlockPos pos, BlockState state, String source, long priority) {
        return new BlockIntent(pos, state, source, priority, IntentType.BLOCK_SET);
    }

    public static BlockIntent breakBlock(BlockPos pos, String source, long priority) {
        return new BlockIntent(pos, null, source, priority, IntentType.BLOCK_BREAK);
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
