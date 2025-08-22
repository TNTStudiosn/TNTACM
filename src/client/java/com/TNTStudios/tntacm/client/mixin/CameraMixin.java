package com.TNTStudios.tntacm.client.mixin;

import com.TNTStudios.tntacm.client.ShipViewController;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    // -- API cámara vanilla
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    // -- Estado interno para suavizado (persistente entre frames)
    @Unique private boolean tntacm$camInit = false;
    @Unique private float tntacm$sYaw = 0f;
    @Unique private float tntacm$sPitch = 0f;
    @Unique private Vec3d tntacm$sPos = Vec3d.ZERO;

    // -- Ganancias del filtro (ajústalas a tu gusto):
    //    α rotación: qué tan rápido sigue el objetivo (0..1). 0.6 = suave sin lag notable.
    @Unique private static final float tntacm$ROT_ALPHA_YAW = 0.60f;
    @Unique private static final float tntacm$ROT_ALPHA_PITCH = 0.55f;
    //    α posición: lerp ligero para matar micro-jitter.
    @Unique private static final double tntacm$POS_ALPHA = 0.65;

    @Inject(method = "update", at = @At("TAIL"))
    private void tntacm$hijackCameraUpdate(BlockView area,
                                           Entity focusedEntity,
                                           boolean thirdPerson,
                                           boolean inverseView,
                                           float tickDelta,
                                           CallbackInfo ci) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        final PlayerEntity player = mc.player;

        // Si no hay jugador o no estamos en la vista de nave, limpio estado y dejo vanilla.
        if (player == null || !ShipViewController.isInShipView) {
            tntacm$camInit = false;
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof NebulaEntity nebula)) {
            tntacm$camInit = false;
            return;
        }

        // ===== POSICIÓN BASE =====
        // Offset hacia adelante de la nave (como cabina/cockpit), usando orientación de la nave.
        final double cameraDistanceOffset = 4.0; // ajustable
        Vec3d shipPos = nebula.getLerpedPos(tickDelta);
        Vec3d shipForward = nebula.getRotationVec(tickDelta);

        // Punto base delante de la nave
        Vec3d targetPos = shipPos.add(shipForward.multiply(cameraDistanceOffset));
        // Altura: uso altura del asiento/montura para que no "respire" con eyeHeight vanilla
        targetPos = new Vec3d(targetPos.x, shipPos.y + nebula.getMountedHeightOffset(), targetPos.z);

        // Suavizado de posición (quita vibración cuando la orientación cambia fuerte)
        if (!tntacm$camInit) {
            tntacm$sPos = targetPos;
        } else {
            tntacm$sPos = new Vec3d(
                    MathHelper.lerp(tntacm$POS_ALPHA, tntacm$sPos.x, targetPos.x),
                    MathHelper.lerp(tntacm$POS_ALPHA, tntacm$sPos.y, targetPos.y),
                    MathHelper.lerp(tntacm$POS_ALPHA, tntacm$sPos.z, targetPos.z)
            );
        }
        this.setPos(tntacm$sPos.x, tntacm$sPos.y, tntacm$sPos.z);

        // ===== ROTACIÓN SUAVIZADA =====
        // La rotación de cámara sale del jugador (entrada del ratón) para que se sienta “1:1”.
        float targetYaw = player.getYaw(tickDelta);
        float targetPitch = player.getPitch(tickDelta);

        // Inicialización del filtro la primera vez
        if (!tntacm$camInit) {
            tntacm$sYaw = targetYaw;
            tntacm$sPitch = targetPitch;
            tntacm$camInit = true;
        } else {
            // Corrijo wrap-around para evitar saltos al cruzar ±180°
            float yawDelta = MathHelper.wrapDegrees(targetYaw - tntacm$sYaw);
            float pitchDelta = targetPitch - tntacm$sPitch; // pitch no necesita wrap

            tntacm$sYaw += yawDelta * tntacm$ROT_ALPHA_YAW;
            tntacm$sPitch += pitchDelta * tntacm$ROT_ALPHA_PITCH;
        }

        // Límite de pitch para no voltear la cámara de más
        tntacm$sPitch = MathHelper.clamp(tntacm$sPitch, -89.9f, 89.9f);

        // Aplico rotación final
        this.setRotation(tntacm$sYaw, tntacm$sPitch);
    }
}
