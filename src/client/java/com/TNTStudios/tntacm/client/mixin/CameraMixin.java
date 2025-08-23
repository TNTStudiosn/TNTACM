package com.TNTStudios.tntacm.client.mixin;

import com.TNTStudios.tntacm.client.ShipViewController;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
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

    // -- Ganancias del filtro (ajústalas a tu gusto)
    @Unique private static final float tntacm$ROT_ALPHA_YAW = 0.60f;
    @Unique private static final float tntacm$ROT_ALPHA_PITCH = 0.55f;
    @Unique private static final double tntacm$POS_ALPHA = 0.65;

    // -- Padding para no pegar la cámara a la cara del bloque
    @Unique private static final double tntacm$CLIP_PADDING = 0.08; // 8cm aprox.

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
        // Offset hacia adelante de la nave (cabina/cockpit), usando orientación de la nave.
        final double cameraDistanceOffset = 5.0; // ajustable
        Vec3d shipPos = nebula.getLerpedPos(tickDelta);
        Vec3d shipForward = nebula.getRotationVec(tickDelta);

        // Punto base delante de la nave
        Vec3d targetPos = shipPos.add(shipForward.multiply(cameraDistanceOffset));
        // Altura: uso altura de montura para estabilidad
        targetPos = new Vec3d(targetPos.x, shipPos.y + nebula.getMountedHeightOffset(), targetPos.z);

        // === CLIP CONTRA BLOQUES (para no ver a través) ===
        // Tiro un raycast desde la cabina al target adelantado; si pego, acerco la cámara al punto de impacto con un margen.
        World world = nebula.getWorld();
        Vec3d originPos = new Vec3d(shipPos.x, shipPos.y + nebula.getMountedHeightOffset(), shipPos.z);
        targetPos = tntacm$clipCamera(world, nebula, originPos, targetPos, tntacm$CLIP_PADDING);

        // Suavizado de posición (quita micro-jitter al cambiar fuerte la orientación)
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
        float targetYaw = player.getYaw(tickDelta);
        float targetPitch = player.getPitch(tickDelta);

        // Inicializo el filtro la primera vez
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

    /**
     * Hago raycast entre 'from' y 'to' y si pego algún bloque visualmente sólido,
     * coloco la cámara justo antes del impacto con un pequeño padding.
     */
    @Unique
    private Vec3d tntacm$clipCamera(World world, Entity source, Vec3d from, Vec3d to, double padding) {
        // Uso ShapeType.VISUAL para respetar la silueta visible (vidrio, etc.) y evitar ver a través.
        RaycastContext ctx = new RaycastContext(
                from, to,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                source
        );

        BlockHitResult hit = world.raycast(ctx);
        if (hit.getType() == HitResult.Type.MISS) {
            // Sin obstáculos: dejo el target original
            return to;
        }

        // Hay impacto: me quedo ligeramente frente a la cara golpeada (hacia afuera del bloque).
        Vec3d hitPos = hit.getPos();
        Direction face = hit.getSide();
        var v = face.getUnitVector();
        Vec3d outwardNormal = new Vec3d(v.x(), v.y(), v.z());
        return hitPos.add(outwardNormal.multiply(padding));

    }
}
