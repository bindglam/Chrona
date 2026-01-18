package org.wrd.chrona.tick.parallel;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.wrd.chrona.tick.phase.BlockIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.WorldSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class RedstoneParallelProcessor {
    private final ExecutorService executor;
    private final IntentMerger intentMerger;

    public RedstoneParallelProcessor(ExecutorService executor, IntentMerger intentMerger) {
        this.executor = executor;
        this.intentMerger = intentMerger;
    }

    public CompletableFuture<Void> processRedstone(WorldSnapshot snapshot, LongList redstonePositions) {
        return processRedstone(snapshot, redstonePositions, intentMerger.getActiveTick());
    }

    public CompletableFuture<Void> processRedstone(WorldSnapshot snapshot, LongList redstonePositions, long tickId) {
        int batchSize = Math.max(1, redstonePositions.size() / Runtime.getRuntime().availableProcessors());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < redstonePositions.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, redstonePositions.size());
            LongList batch = redstonePositions.subList(start, end);

            futures.add(CompletableFuture.runAsync(() -> processBatch(snapshot, batch, tickId), executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void processBatch(WorldSnapshot snapshot, LongList positions, long tickId) {
        for (long posLong : positions) {
            BlockPos pos = BlockPos.of(posLong);
            BlockState state = snapshot.getBlockState(pos);

            if (state.getBlock() instanceof RedStoneWireBlock) {
                processRedstoneWire(snapshot, pos, state, tickId);
            }
        }
    }

    private void processRedstoneWire(WorldSnapshot snapshot, BlockPos pos, BlockState state, long tickId) {
        int newPower = calculatePower(snapshot, pos);
        int currentPower = state.getValue(RedStoneWireBlock.POWER);

        if (newPower != currentPower) {
            BlockState newState = state.setValue(RedStoneWireBlock.POWER, newPower);
            intentMerger.submit(BlockIntent.set(
                pos,
                newState,
                "redstone",
                System.nanoTime()
            ), tickId);
        }
    }

    private int calculatePower(WorldSnapshot snapshot, BlockPos pos) {
        int maxPower = 0;

        // Check all 6 directions for power sources
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = snapshot.getBlockState(neighborPos);

            if (neighborState == null) continue;

            // Direct power from neighbor
            try {
                int directPower = neighborState.getSignal(null, neighborPos, direction);
                maxPower = Math.max(maxPower, directPower);
            } catch (Exception e) {
                // getSignal might fail in snapshot context, continue
            }

            // Power from redstone wire (decreases by 1 per block)
            if (neighborState.getBlock() instanceof RedStoneWireBlock) {
                int wirePower = neighborState.getValue(RedStoneWireBlock.POWER);
                maxPower = Math.max(maxPower, wirePower - 1);
            }

            // Power from redstone components (repeaters, comparators, etc.)
            if (neighborState.getBlock() instanceof net.minecraft.world.level.block.DiodeBlock) {
                try {
                    if (((net.minecraft.world.level.block.DiodeBlock) neighborState.getBlock()).isLocked(null, neighborPos, neighborState)) {
                        continue;
                    }
                    int diodePower = neighborState.getValue(net.minecraft.world.level.block.DiodeBlock.POWERED) ? 15 : 0;
                    maxPower = Math.max(maxPower, diodePower);
                } catch (Exception e) {
                    // DiodeBlock access might fail, continue
                }
            }

            // Power from blocks being powered (torches, levers, buttons, etc.)
            try {
                if (neighborState.isRedstoneConductor(null, neighborPos)) {
                    // Check if this solid block is powered from another side
                    for (net.minecraft.core.Direction indirectDir : net.minecraft.core.Direction.values()) {
                        if (indirectDir == direction.getOpposite()) continue;

                        BlockPos indirectPos = neighborPos.relative(indirectDir);
                        BlockState indirectState = snapshot.getBlockState(indirectPos);

                        if (indirectState == null) continue;

                        try {
                            int indirectPower = indirectState.getSignal(null, indirectPos, indirectDir);
                            maxPower = Math.max(maxPower, indirectPower);
                        } catch (Exception ex) {
                            // Continue on error
                        }
                    }
                }
            } catch (Exception e) {
                // Continue on error
            }
        }

        // Clamp power to 0-15 range
        return Math.max(0, Math.min(15, maxPower));
    }
}
