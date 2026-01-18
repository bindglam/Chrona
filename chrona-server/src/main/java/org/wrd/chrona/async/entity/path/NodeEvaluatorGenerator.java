package org.wrd.chrona.async.entity.path;

import net.minecraft.world.level.pathfinder.NodeEvaluator;

@FunctionalInterface
public interface NodeEvaluatorGenerator {
    NodeEvaluator generate(NodeEvaluatorFeatures nodeEvaluatorFeatures);
}
