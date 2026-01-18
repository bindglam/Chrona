package org.wrd.chrona.async.entity.path;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class AsyncPath extends Path {
    private volatile boolean ready = false;
    private final ConcurrentLinkedQueue<Runnable> postProcessing = new ConcurrentLinkedQueue<>();
    private final Set<BlockPos> processingPositions;
    private final Supplier<Path> pathSupplier;

    private final List<Node> nodes;
    private BlockPos target;
    private float distToTarget = 0.0F;
    private boolean canReach = true;

    public AsyncPath(@NotNull List<Node> emptyNodeList, @NotNull Set<BlockPos> targets, @NotNull Supplier<Path> pathSupplier) {
        super(emptyNodeList, null, false);
        this.nodes = emptyNodeList;
        this.processingPositions = targets;
        this.pathSupplier = pathSupplier;

        AsyncPathProcessor.queue(this);
    }

    @Override
    public boolean isProcessed() {
        return this.ready;
    }

    public final void schedulePostProcessing(@NotNull Runnable runnable) {
        if (this.ready) {
            runnable.run();
        } else {
            this.postProcessing.offer(runnable);
            if (this.ready) {
                this.runAllPostProcessing(true);
            }
        }
    }

    public final boolean hasSameProcessingPositions(final Set<BlockPos> targets) {
        if (this.processingPositions.size() != targets.size()) {
            return false;
        }

        if (targets.size() == 1) {
            return this.processingPositions.iterator().next().equals(targets.iterator().next());
        }

        return this.processingPositions.containsAll(targets);
    }

    public final void process() {
        if (this.ready) return;

        synchronized (this) {
            if (this.ready) return;
            final Path computed = this.pathSupplier.get();
            if (computed != null) {
                this.nodes.addAll(computed.nodes);
                this.target = computed.getTarget();
                this.distToTarget = computed.getDistToTarget();
                this.canReach = computed.canReach();
            } else {
                this.target = null;
                this.distToTarget = 0.0F;
                this.canReach = false;
            }
            this.ready = true;
        }

        this.runAllPostProcessing(TickThread.isTickThread());
    }

    private void runAllPostProcessing(boolean isTickThread) {
        Runnable runnable;
        while ((runnable = this.postProcessing.poll()) != null) {
            if (isTickThread) {
                runnable.run();
            } else {
                MinecraftServer.getServer().scheduleOnMain(runnable);
            }
        }
    }

    @Override
    public @NotNull BlockPos getTarget() {
        this.process();
        return this.target;
    }

    @Override
    public float getDistToTarget() {
        this.process();
        return this.distToTarget;
    }

    @Override
    public boolean canReach() {
        this.process();
        return this.canReach;
    }

    @Override
    public boolean isDone() {
        return this.ready && super.isDone();
    }

    @Override
    public void advance() {
        this.process();
        super.advance();
    }

    @Override
    public boolean notStarted() {
        this.process();
        return super.notStarted();
    }

    @Override
    public @Nullable Node getEndNode() {
        this.process();
        return super.getEndNode();
    }

    @Override
    public @NotNull Node getNode(int index) {
        this.process();
        return super.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        this.process();
        super.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, @NotNull Node node) {
        this.process();
        super.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        this.process();
        return super.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        this.process();
        return super.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int nodeIndex) {
        this.process();
        super.setNextNodeIndex(nodeIndex);
    }

    @Override
    public @NotNull Vec3 getEntityPosAtNode(@NotNull Entity entity, int index) {
        this.process();
        return super.getEntityPosAtNode(entity, index);
    }

    @Override
    public @NotNull BlockPos getNodePos(int index) {
        this.process();
        return super.getNodePos(index);
    }

    @Override
    public @NotNull Vec3 getNextEntityPos(@NotNull Entity entity) {
        this.process();
        return super.getNextEntityPos(entity);
    }

    @Override
    public @NotNull BlockPos getNextNodePos() {
        this.process();
        return super.getNextNodePos();
    }

    @Override
    public @NotNull Node getNextNode() {
        this.process();
        return super.getNextNode();
    }

    @Override
    public @Nullable Node getPreviousNode() {
        this.process();
        return super.getPreviousNode();
    }
}
