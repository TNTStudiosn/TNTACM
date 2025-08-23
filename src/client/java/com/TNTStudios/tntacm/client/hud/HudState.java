package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

//region State Management
public class HudState {

    //region Constants
    private static final float LERP_SPEED = 0.12f;
    private static final float LERP_VEL_2D = 0.18f;
    private static final float LERP_ANGULAR = 0.25f;
    private static final float LERP_HEALTH = 0.08f;
    private static final float LERP_PROXIMITY = 0.30f;
    private static final double PROXIMITY_WARNING_DIST = 25.0;
    //endregion

    //region Smoothed Values
    public double smoothedSpeed = 0.0;
    public double smoothVelX = 0.0;
    public double smoothVelY = 0.0;
    public double smoothAngularVel = 0.0;
    public double smoothGForce = 0.0;
    public double smoothHealth = 1.0;
    public double smoothEnergy = 1.0;
    public double proximityAlert = 0.0;
    //endregion

    //region History
    private Vec3d lastVel = Vec3d.ZERO;
    private float lastYaw = 0f;
    //endregion

    public void update(NebulaEntity ship, float tickDelta, float dt, long now) {
        // Velocity and Speed
        final Vec3d vel = ship.getVelocity();
        final double speedBps = vel.length() * 20.0;
        smoothedSpeed = HudUtils.lerpExp(smoothedSpeed, speedBps, LERP_SPEED, dt);

        // Angular Velocity
        final float yawNow = ship.getYaw(tickDelta);
        final double yawDelta = Math.abs(MathHelper.wrapDegrees(yawNow - lastYaw));
        final double angularVel = yawDelta / Math.max(0.001f, dt);
        smoothAngularVel = HudUtils.lerpExp(smoothAngularVel, angularVel, LERP_ANGULAR, dt);

        // G-Force
        final Vec3d dVel = vel.subtract(lastVel);
        final double gApprox = dVel.length() * 20.0;
        smoothGForce = HudUtils.lerpExp(smoothGForce, gApprox, 0.18f, dt);

        // Health and Energy
        final float hp = MathHelper.clamp(ship.getHealth() / ship.getMaxHealth(), 0f, 1f);
        smoothHealth = HudUtils.lerpExp(smoothHealth, hp, LERP_HEALTH, dt);

        final double energyTarget = Math.max(0.05, Math.min(1.0, 0.72 + 0.22 * Math.sin(now * 0.0011) + 0.08 * Math.sin(now * 0.006)));
        smoothEnergy = HudUtils.lerpExp(smoothEnergy, energyTarget, LERP_HEALTH, dt);

        // Proximity Alert
        final Box proximityBox = Box.of(ship.getPos(), PROXIMITY_WARNING_DIST * 2, PROXIMITY_WARNING_DIST * 2, PROXIMITY_WARNING_DIST * 2);
        boolean nearbyThreat = !ship.getWorld().getOtherEntities(ship, proximityBox, e -> e.isAlive() && !(e instanceof NebulaEntity)).isEmpty();
        final double targetAlert = nearbyThreat ? 1.0 : 0.0;
        proximityAlert = HudUtils.lerpExp(proximityAlert, targetAlert, LERP_PROXIMITY, dt);

        // Flight Path Marker velocity
        float shipYaw = ship.getYaw(tickDelta);
        Vec3d forward = ship.getRotationVec(tickDelta).normalize();
        Vec3d right = Vec3d.fromPolar(0, shipYaw - 90).normalize();
        Vec3d up = right.crossProduct(forward).normalize();
        Vec3d dir = vel.normalize();
        double sx = dir.dotProduct(right);
        double sy = dir.dotProduct(up);
        smoothVelX = HudUtils.lerpExp(smoothVelX, sx, LERP_VEL_2D, dt);
        smoothVelY = HudUtils.lerpExp(smoothVelY, sy, LERP_VEL_2D, dt);

        // Update history
        this.lastVel = vel;
        this.lastYaw = yawNow;
    }
}
//endregion