package org.wrd.chrona.tick.overlay;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class OverlayWorldView {
    private static final ThreadLocal<OverlayWorldView> CURRENT = new ThreadLocal<>();

    private final Long2ObjectMap<BlockState> overlayBlocks = new Long2ObjectOpenHashMap<>();

    public static OverlayWorldView current() {
        OverlayWorldView view = CURRENT.get();
        if (view == null) {
            view = new OverlayWorldView();
            CURRENT.set(view);
        }
        return view;
    }

    public static void setCurrent(OverlayWorldView view) {
        CURRENT.set(view);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void setBlock(BlockPos pos, BlockState state) {
        overlayBlocks.put(pos.asLong(), state);
    }

    @Nullable
    public BlockState getOverlay(BlockPos pos) {
        return overlayBlocks.get(pos.asLong());
    }

    public boolean hasOverlay(BlockPos pos) {
        return overlayBlocks.containsKey(pos.asLong());
    }

    public void reset() {
        overlayBlocks.clear();
    }
}
