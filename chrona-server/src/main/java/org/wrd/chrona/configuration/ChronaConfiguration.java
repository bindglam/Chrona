package org.wrd.chrona.configuration;

import org.wrd.chrona.async.entity.path.PathfindTaskRejectPolicy;

import java.io.File;

public final class ChronaConfiguration extends Configuration {
    private static final int CONFIG_VERSION = 100;
    private static final File CONFIG_FILE = new File("chrona.yml");

    public final Optimization optimization = new Optimization();
    public final class Optimization {
        public final Field<Boolean> minimizeTPSLag = createField("optimization.minimize-tps-lag", false);

        public final Async async = new Async();
        public final class Async {
            public final Pathfinding pathfinding = new Pathfinding();
            public final class Pathfinding {
                public final Field<Boolean> enabled = createField("optimization.async.pathfinding.enabled", false);
                public final Field<Integer> maxThreads = createField("optimization.async.pathfinding.max-threads", 0);
                public final Field<Integer> keepalive = createField("optimization.async.pathfinding.keepalive", 60);
                public final Field<Integer> queueSize = createField("optimization.async.pathfinding.queue-size", 0);
                public final Field<String> rejectPolicy = createField("optimization.async.pathfinding.reject-policy", PathfindTaskRejectPolicy.FLUSH_ALL.name());
            }

            public final MobSpawning mobSpawning = new MobSpawning();
            public final class MobSpawning {
                public final Field<Boolean> enabled = createField("optimization.async.mob-spawning.enabled", false);
            }

            public final ChunkSending chunkSending = new ChunkSending();
            public final class ChunkSending {
                public final Field<Boolean> enabled = createField("optimization.async.chunk-sending.enabled", false);
            }
        }

        public final MaxProjectileChunkLoads maxProjectileChunkLoads = new MaxProjectileChunkLoads();
        public final class MaxProjectileChunkLoads {
            public final PerProjectile perProjectile = new PerProjectile();
            public final class PerProjectile {
                public final Field<Integer> max = createField("optimization.max-projectile-chunk-loads.per-projectile.max", 10);
                public final Field<Boolean> removeFromWorldAfterReachLimit = createField("optimization.max-projectile-chunk-loads.per-projectile.remove-from-world-after-reach-limit", false);
                public final Field<Boolean> resetMovementAfterReachLimit = createField("optimization.max-projectile-chunk-loads.per-projectile.reset-movement-after-reach-limit", false);
            }
            public final Field<Integer> perTick = createField("optimization.max-projectile-chunk-loads.per-tick", 10);
        }
    }


    public ChronaConfiguration() {
        super(CONFIG_FILE);
    }
}
