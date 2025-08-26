// src/client/java/com/TNTStudios/tntacm/client/TntacmClient.java
package com.TNTStudios.tntacm.client;

import com.TNTStudios.tntacm.client.entity.NebulaRenderer;
import com.TNTStudios.tntacm.client.entity.projectile.LaserProjectileRenderer;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.networking.ModMessages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TntacmClient implements ClientModInitializer {

    // FIX: La constante fue eliminada en 1.20.1, así que la defino aquí para mantener el código limpio.
    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);
    // Defino qué tan adelante de la cámara nacerá el proyectil. 1.0 = 1 bloque.
    private static final double PROJECTILE_SPAWN_OFFSET = 5.0;

    @Override
    public void onInitializeClient() {
        // Renderers
        EntityRendererRegistry.register(ModEntities.NEBULA, NebulaRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLUE_LASER_PROJECTILE, LaserProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.RED_LASER_PROJECTILE, LaserProjectileRenderer::new);

        // Atributos (necesarios también en cliente para predicción)
        FabricDefaultAttributeRegistry.register(ModEntities.NEBULA, NebulaEntity.setAttributes());

        // Paquetes y Eventos
        registerPacketHandlers();
        registerInputHandlers();
    }

    //region Networking
    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ENTER_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = true;
                client.options.setPerspective(Perspective.FIRST_PERSON);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.EXIT_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = false;
            });
        });
    }
    //endregion

    //region Input Handling
    private void registerInputHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !ShipViewController.isInShipView || client.gameRenderer == null) {
                return;
            }

            if (client.options.attackKey.isPressed()) {
                //region Packet
                Camera camera = client.gameRenderer.getCamera();
                Vec3d cameraPos = camera.getPos();
                Vec3d cameraDir = getRotationVectorFromAngles(camera.getPitch(), camera.getYaw());

                // Calculo el punto de origen adelantado, usando la posición y dirección de la cámara.
                Vec3d spawnOrigin = cameraPos.add(cameraDir.multiply(PROJECTILE_SPAWN_OFFSET));

                PacketByteBuf buf = PacketByteBufs.create();

                // Uso el nuevo origen adelantado para el paquete.
                buf.writeDouble(spawnOrigin.x);
                buf.writeDouble(spawnOrigin.y);
                buf.writeDouble(spawnOrigin.z);

                buf.writeDouble(cameraDir.x);
                buf.writeDouble(cameraDir.y);
                buf.writeDouble(cameraDir.z);

                ClientPlayNetworking.send(ModMessages.FIRE_PROJECTILE_ID, buf);
                //endregion
            }
        });
    }
    //endregion

    /**
     * Calcula el vector de dirección a partir de los ángulos de pitch y yaw.
     * Es una réplica del método `protected` de la clase `Entity` para poder usarlo aquí.
     */
    private static Vec3d getRotationVectorFromAngles(float pitch, float yaw) {
        // FIX: Reemplazo la constante `MathHelper.DEGREES_TO_RADIANS` que ya no existe en 1.20.1.
        float pitchRad = pitch * DEGREES_TO_RADIANS;
        float yawRad = -yaw * DEGREES_TO_RADIANS;

        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}