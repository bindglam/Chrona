package org.wrd.chrona.tick.parallel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.phase.EntityIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.IntentPriority;
import org.wrd.chrona.tick.phase.WorldSnapshot;

/**
 * Physics intent computer that calculates gravity, drag, and collision
 * Generates ENTITY_MOVE intents based on physics simulation
 */
public class PhysicsIntentComputer implements EntityIntentComputer {

    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final double WATER_DRAG = 0.8;
    private static final double TERMINAL_VELOCITY = -3.92;
    private static final double MIN_VELOCITY = 0.003;

    @Override
    public boolean supports(Entity entity, WorldSnapshot.EntitySnapshot snapshot) {
        if (!ChronaConfig.physicsParallel) {
            return false;
        }
        // Skip players (handled by client/server sync)
        if (entity instanceof Player) {
            return false;
        }
        // Support entities that are affected by physics
        return entity instanceof ItemEntity ||
               entity instanceof FallingBlockEntity ||
               entity instanceof Projectile ||
               (entity instanceof LivingEntity && !entity.isNoGravity());
    }

    @Override
    public void compute(WorldSnapshot snapshot, Entity entity, WorldSnapshot.EntitySnapshot entitySnapshot,
                        IntentMerger intentMerger, long tickId) {
        Vec3 pos = entitySnapshot.position();
        Vec3 vel = entitySnapshot.velocity();
        boolean onGround = entitySnapshot.onGround();
        boolean inWater = entitySnapshot.inWater();

        // Apply gravity
        Vec3 newVel = applyGravity(vel, entity.isNoGravity(), inWater);

        // Apply drag
        newVel = applyDrag(newVel, onGround, inWater);

        // Clamp to terminal velocity
        if (newVel.y < TERMINAL_VELOCITY) {
            newVel = new Vec3(newVel.x, TERMINAL_VELOCITY, newVel.z);
        }

        // Zero out very small velocities
        newVel = clampSmallVelocity(newVel);

        // Calculate new position
        Vec3 newPos = pos.add(newVel);

        // Check collision and adjust
        CollisionResult collision = checkCollision(snapshot, pos, newPos, newVel);
        newPos = collision.position;
        newVel = collision.velocity;

        // Only submit intent if there's meaningful movement
        if (!isNegligibleChange(pos, newPos, vel, newVel)) {
            intentMerger.submit(
                EntityIntent.move(
                    entitySnapshot.id(),
                    newPos,
                    newVel,
                    "physics",
                    IntentPriority.withTimestamp(IntentPriority.PHYSICS)
                ),
                tickId
            );
        }
    }

    private Vec3 applyGravity(Vec3 vel, boolean noGravity, boolean inWater) {
        if (noGravity) {
            return vel;
        }
        double gravityMod = inWater ? GRAVITY * 0.25 : GRAVITY;
        return new Vec3(vel.x, vel.y - gravityMod, vel.z);
    }

    private Vec3 applyDrag(Vec3 vel, boolean onGround, boolean inWater) {
        double drag;
        if (inWater) {
            drag = WATER_DRAG;
        } else if (onGround) {
            drag = 0.91; // Ground friction
        } else {
            drag = AIR_DRAG;
        }
        return new Vec3(vel.x * drag, vel.y * AIR_DRAG, vel.z * drag);
    }

    private Vec3 clampSmallVelocity(Vec3 vel) {
        double x = Math.abs(vel.x) < MIN_VELOCITY ? 0 : vel.x;
        double y = Math.abs(vel.y) < MIN_VELOCITY ? 0 : vel.y;
        double z = Math.abs(vel.z) < MIN_VELOCITY ? 0 : vel.z;
        return new Vec3(x, y, z);
    }

    private CollisionResult checkCollision(WorldSnapshot snapshot, Vec3 oldPos, Vec3 newPos, Vec3 vel) {
        BlockPos blockPos = BlockPos.containing(newPos.x, newPos.y, newPos.z);
        BlockPos blockPosBelow = blockPos.below();

        // Simple collision check - see if the new position collides
        boolean collisionAtNew = snapshot.hasCollision(blockPos);
        boolean collisionBelow = snapshot.hasCollision(blockPosBelow);

        if (collisionAtNew) {
            // Can't move to new position, stay at old
            return new CollisionResult(oldPos, Vec3.ZERO);
        }

        if (vel.y < 0 && collisionBelow) {
            // Hitting ground - stop vertical movement
            double groundY = blockPosBelow.getY() + 1.0;
            return new CollisionResult(
                new Vec3(newPos.x, groundY, newPos.z),
                new Vec3(vel.x, 0, vel.z)
            );
        }

        return new CollisionResult(newPos, vel);
    }

    private boolean isNegligibleChange(Vec3 oldPos, Vec3 newPos, Vec3 oldVel, Vec3 newVel) {
        double posDiff = oldPos.distanceToSqr(newPos);
        double velDiff = oldVel.distanceToSqr(newVel);
        return posDiff < 0.0001 && velDiff < 0.0001;
    }

    private record CollisionResult(Vec3 position, Vec3 velocity) {}
}
