package org.wrd.chrona.configuration;

import org.wrd.chrona.async.entity.path.PathfindTaskRejectPolicy;

import java.io.File;

public final class ChronaConfiguration extends Configuration {
    private static final int CONFIG_VERSION = 100;
    private static final File CONFIG_FILE = new File("chrona.yml");

    public final Field<Boolean> minimizeTPSLag = createField("optimization.minimize-tps-lag", false);

    public final Field<Boolean> optimizationAsyncPathfindingEnabled = createField("optimization.async.pathfinding.enabled", false);
    public final Field<Integer> optimizationAsyncPathfindingMaxThreads = createField("optimization.async.pathfinding.max-threads", 0);
    public final Field<Integer> optimizationAsyncPathfindingKeepalive = createField("optimization.async.pathfinding.keepalive", 60);
    public final Field<Integer> optimizationAsyncPathfindingQueueSize = createField("optimization.async.pathfinding.queue-size", 0);
    public final Field<String> optimizationAsyncPathfindingRejectPolicy = createField("optimization.async.pathfinding.reject-policy", PathfindTaskRejectPolicy.FLUSH_ALL.name());

    public final Field<Boolean> httpServerEnabled = createField("http-server.enabled", false);
    public final Field<Integer> httpServerPort = createField("http-server.port", 3000);

    public ChronaConfiguration() {
        super(CONFIG_FILE);
    }
}
