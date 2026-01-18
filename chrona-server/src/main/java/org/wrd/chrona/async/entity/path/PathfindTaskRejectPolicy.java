package org.wrd.chrona.async.entity.path;

public enum PathfindTaskRejectPolicy {
    ABORT,
    CALLER_RUNS,
    DISCARD_OLDEST,
    FLUSH_ALL
}
