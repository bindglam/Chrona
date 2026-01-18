package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.phase.EntityIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.IntentPriority;
import org.wrd.chrona.tick.phase.WorldSnapshot;

import java.util.List;

/**
 * AI intent computer for Mob entities
 * Evaluates goals and generates movement/attack intents
 */
public class AIIntentComputer implements EntityIntentComputer {

    private static final double MOVE_SPEED = 0.25;
    private static final double ATTACK_RANGE = 2.0;
    private static final double ATTACK_DAMAGE = 2.0f;

    @Override
    public boolean supports(Entity entity, WorldSnapshot.EntitySnapshot snapshot) {
        if (!ChronaConfig.aiParallel) {
            return false;
        }
        // Only support Mob entities (not players)
        return entity instanceof Mob && !(entity instanceof Player);
    }

    @Override
    public void compute(WorldSnapshot snapshot, Entity entity, WorldSnapshot.EntitySnapshot entitySnapshot,
                        IntentMerger intentMerger, long tickId) {
        if (!(entity instanceof Mob mob)) {
            return;
        }

        Vec3 currentPos = entitySnapshot.position();
        Integer targetId = entitySnapshot.targetId();

        // Try to find or validate target
        WorldSnapshot.EntitySnapshot targetSnapshot = null;
        if (targetId != null) {
            targetSnapshot = snapshot.getEntity(targetId);
            // Validate target is still valid
            if (targetSnapshot == null || targetSnapshot.health() <= 0) {
                targetId = null;
                targetSnapshot = null;
            }
        }

        // If no target, try to find one (for hostile mobs)
        if (targetId == null && shouldSearchTarget(mob)) {
            targetSnapshot = findNearestTarget(snapshot, entitySnapshot, mob);
        }

        // Generate intents based on AI state
        if (targetSnapshot != null) {
            processTargetAI(snapshot, entitySnapshot, targetSnapshot, mob, intentMerger, tickId);
        } else {
            processIdleAI(snapshot, entitySnapshot, mob, intentMerger, tickId);
        }
    }

    private boolean shouldSearchTarget(Mob mob) {
        // Monsters actively search for players
        return mob instanceof Monster;
    }

    private WorldSnapshot.EntitySnapshot findNearestTarget(
            WorldSnapshot snapshot,
            WorldSnapshot.EntitySnapshot self,
            Mob mob) {

        List<WorldSnapshot.EntitySnapshot> nearby = snapshot.getEntitiesInRadius(
            self.position(),
            ChronaConfig.aiTargetSearchRadius
        );

        WorldSnapshot.EntitySnapshot nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (WorldSnapshot.EntitySnapshot candidate : nearby) {
            // Skip self
            if (candidate.id() == self.id()) {
                continue;
            }

            // Skip dead entities
            if (candidate.health() <= 0) {
                continue;
            }

            // For monsters, target players (check if it's a living entity)
            if (!candidate.isLiving()) {
                continue;
            }

            // For monsters, prefer players
            if (mob instanceof Monster && !candidate.isPlayer()) {
                continue;
            }

            double distSq = self.position().distanceToSqr(candidate.position());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private void processTargetAI(
            WorldSnapshot snapshot,
            WorldSnapshot.EntitySnapshot self,
            WorldSnapshot.EntitySnapshot target,
            Mob mob,
            IntentMerger intentMerger,
            long tickId) {

        Vec3 selfPos = self.position();
        Vec3 targetPos = target.position();
        double distance = selfPos.distanceTo(targetPos);

        // Check if in attack range
        if (distance <= ATTACK_RANGE) {
            // Generate damage intent
            float damage = (float) getAttackDamage(mob);
            intentMerger.submit(
                EntityIntent.damage(
                    target.id(),
                    damage,
                    "entity_ai",
                    IntentPriority.withTimestamp(IntentPriority.ENTITY_AI)
                ),
                tickId
            );
        } else {
            // Move towards target
            Vec3 direction = targetPos.subtract(selfPos).normalize();
            double speed = getMoveSpeed(mob);
            Vec3 movement = direction.scale(speed);
            Vec3 newPos = selfPos.add(movement);

            // Keep Y same if on ground (simple pathing)
            if (self.onGround()) {
                newPos = new Vec3(newPos.x, selfPos.y, newPos.z);
            }

            intentMerger.submit(
                EntityIntent.move(
                    self.id(),
                    newPos,
                    movement,
                    "entity_ai",
                    IntentPriority.withTimestamp(IntentPriority.ENTITY_AI)
                ),
                tickId
            );
        }
    }

    private void processIdleAI(
            WorldSnapshot snapshot,
            WorldSnapshot.EntitySnapshot self,
            Mob mob,
            IntentMerger intentMerger,
            long tickId) {

        // Simple idle behavior - random movement occasionally
        // This is a simplified version; real AI would use goal selectors

        // Check if mob should wander
        if (mob.getRandom().nextFloat() < 0.02f) { // 2% chance per tick
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double distance = mob.getRandom().nextDouble() * 4 + 2; // 2-6 blocks

            Vec3 currentPos = self.position();
            Vec3 offset = new Vec3(
                Math.cos(angle) * distance,
                0,
                Math.sin(angle) * distance
            );

            Vec3 targetPos = currentPos.add(offset);

            // Check if target is valid (not in collision)
            if (!snapshot.hasCollision(net.minecraft.core.BlockPos.containing(targetPos.x, targetPos.y, targetPos.z))) {
                Vec3 direction = offset.normalize();
                double speed = getMoveSpeed(mob) * 0.5; // Slower wandering
                Vec3 movement = direction.scale(speed);

                intentMerger.submit(
                    EntityIntent.move(
                        self.id(),
                        currentPos.add(movement),
                        movement,
                        "entity_ai",
                        IntentPriority.withTimestamp(IntentPriority.ENTITY_AI)
                    ),
                    tickId
                );
            }
        }
    }

    private double getMoveSpeed(Mob mob) {
        // Try to get actual speed attribute
        try {
            return mob.getSpeed();
        } catch (Exception e) {
            return MOVE_SPEED;
        }
    }

    private double getAttackDamage(Mob mob) {
        // Try to get actual attack damage attribute
        try {
            if (mob instanceof LivingEntity living) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                    living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    return attr.getValue();
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return ATTACK_DAMAGE;
    }
}
