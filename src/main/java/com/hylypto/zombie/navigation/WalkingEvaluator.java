package com.hylypto.zombie.navigation;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarEvaluator;
import com.hypixel.hytale.server.npc.navigation.AStarNode;

/**
 * Simple A* evaluator for ground-based NPC navigation.
 * Euclidean distance heuristic, configurable arrival threshold.
 */
public class WalkingEvaluator implements AStarEvaluator {

    private final Vector3d goal;
    private final double arrivalThreshold;

    public WalkingEvaluator(Vector3d goal, double arrivalThreshold) {
        this.goal = goal;
        this.arrivalThreshold = arrivalThreshold;
    }

    @Override
    public float estimateToGoal(AStarBase astar, Vector3d position, MotionController controller) {
        return (float) position.distanceTo(goal);
    }

    @Override
    public boolean isGoalReached(Ref<EntityStore> ref, AStarBase astar, AStarNode node,
                                  MotionController controller, ComponentAccessor<EntityStore> accessor) {
        return node.getPosition().distanceTo(goal) <= arrivalThreshold;
    }
}
