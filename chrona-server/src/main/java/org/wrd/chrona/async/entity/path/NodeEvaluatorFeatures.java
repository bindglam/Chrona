package org.wrd.chrona.async.entity.path;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;

import java.util.Objects;

public class NodeEvaluatorFeatures {
    private final NodeEvaluatorType type;
    private final boolean allowBreaching;
    private final boolean canPassDoors;
    private final boolean canFloat;
    private final boolean canWalkOverFences;
    private final boolean canOpenDoors;

    public NodeEvaluatorFeatures(NodeEvaluatorType type, boolean allowBreaching, boolean canPassDoors, boolean canFloat,
                                 boolean canWalkOverFences, boolean canOpenDoors) {
        this.type = type;
        this.allowBreaching = allowBreaching;
        this.canPassDoors = canPassDoors;
        this.canFloat = canFloat;
        this.canWalkOverFences = canWalkOverFences;
        this.canOpenDoors = canOpenDoors;
    }

    public static NodeEvaluatorFeatures fromNodeEvaluator(NodeEvaluator nodeEvaluator) {
        NodeEvaluatorType type = NodeEvaluatorType.fromNodeEvaluator(nodeEvaluator);
        boolean canPassDoors = nodeEvaluator.canPassDoors();
        boolean canFloat = nodeEvaluator.canFloat();
        boolean canWalkOverFences = nodeEvaluator.canWalkOverFences();
        boolean canOpenDoors = nodeEvaluator.canOpenDoors();
        boolean allowBreaching = nodeEvaluator instanceof SwimNodeEvaluator swimNodeEvaluator && swimNodeEvaluator.allowBreaching;
        return new NodeEvaluatorFeatures(type, allowBreaching, canPassDoors, canFloat, canWalkOverFences, canOpenDoors);
    }

    public NodeEvaluatorType type() {
        return type;
    }

    public boolean allowBreaching() {
        return allowBreaching;
    }

    public boolean canPassDoors() {
        return canPassDoors;
    }

    public boolean canFloat() {
        return canFloat;
    }

    public boolean canWalkOverFences() {
        return canWalkOverFences;
    }

    public boolean canOpenDoors() {
        return canOpenDoors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeEvaluatorFeatures that = (NodeEvaluatorFeatures) o;
        return allowBreaching == that.allowBreaching &&
                canPassDoors == that.canPassDoors &&
                canFloat == that.canFloat &&
                canWalkOverFences == that.canWalkOverFences &&
                canOpenDoors == that.canOpenDoors &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, allowBreaching, canPassDoors, canFloat, canWalkOverFences, canOpenDoors);
    }
}
