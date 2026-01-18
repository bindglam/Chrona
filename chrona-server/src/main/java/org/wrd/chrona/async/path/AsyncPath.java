package org.wrd.chrona.async.path;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class AsyncPath extends Path {
    private volatile boolean ready = false;
    private final ConcurrentLinkedQueue<Runnable> postProcessing = new ConcurrentLinkedQueue<>();
    private final Supplier<Path> pathSupplier;

    public AsyncPath(List<Node> nodes, Supplier<Path> pathSupplier) {
        super(nodes, null, false);
        this.pathSupplier = pathSupplier;
        AsyncPathProcessor.queue(this);
    }

    public final void process() {
        if (ready) return;

        synchronized (this) {
            if (ready) return;

            Path computed = pathSupplier.get();
            if (computed != null) {
                this.nodes.clear();
                this.nodes.addAll(computed.nodes);
                // target is handled by Path superclass
            }

            ready = true;
        }

        runAllPostProcessing();
    }

    public final void schedulePostProcessing(@NotNull Runnable runnable) {
        if (ready) {
            runnable.run();
        } else {
            postProcessing.offer(runnable);
            if (ready) {
                runAllPostProcessing();
            }
        }
    }

    private void runAllPostProcessing() {
        Runnable task;
        while ((task = postProcessing.poll()) != null) {
            task.run();
        }
    }

    @Override
    public boolean isProcessed() {
        return ready;
    }
}
