package org.wrd.chrona.async.entity.path;

import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;

public enum NodeEvaluatorType {
    WALK,
    SWIM,
    AMPHIBIOUS,
    FLY;

    public static NodeEvaluatorType fromNodeEvaluator(NodeEvaluator nodeEvaluator) {
        if (nodeEvaluator instanceof SwimNodeEvaluator) return SWIM;
        if (nodeEvaluator instanceof FlyNodeEvaluator) return FLY;
        if (nodeEvaluator instanceof AmphibiousNodeEvaluator) return AMPHIBIOUS;
        return WALK;
    }

    public static NodeEvaluatorType fromNavigation(PathNavigation navigation) {
        if (navigation instanceof WaterBoundPathNavigation) return SWIM;
        if (navigation instanceof FlyingPathNavigation) return FLY;
        if (navigation instanceof AmphibiousPathNavigation) return AMPHIBIOUS;
        return WALK;
    }
}
