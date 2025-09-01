// src/client/java/com/TNTStudios/tntacm/client/sound/NebulaEngineSoundInstance.java
package com.TNTStudios.tntacm.client.sound;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

public class NebulaEngineSoundInstance extends MovingSoundInstance {
    private final NebulaEntity nebula;

    public NebulaEngineSoundInstance(NebulaEntity nebula) {
        // El sonido se repite, no se atenúa con la distancia (siempre estamos "en" la nave)
        // y lo vinculo al SoundEvent que registramos.
        super(ModSounds.ENTITY_NEBULA_ENGINE, SoundCategory.NEUTRAL, SoundInstance.createRandom());
        this.nebula = nebula;
        this.repeat = true;
        this.repeatDelay = 0;
        // FIX: Empiezo con un volumen minúsculo en lugar de 0.0F.
        // Esto asegura que el SoundManager procese el sonido y llame a tick(),
        // permitiendo que el volumen aumente dinámicamente como queremos.
        this.volume = 0.001F;
        // La posición del sonido es la de la nave.
        this.x = (float)nebula.getX();
        this.y = (float)nebula.getY();
        this.z = (float)nebula.getZ();
    }

    @Override
    public void tick() {
        // === LA CORRECCIÓN ESTÁ AQUÍ ===
        // Obtengo una referencia al jugador del cliente.
        PlayerEntity player = MinecraftClient.getInstance().player;

        // En lugar de `!this.nebula.hasPassengers()`, compruebo si el vehículo del jugador
        // sigue siendo esta instancia de la Nébula. Esto es mucho más seguro contra
        // desincronizaciones de un solo tick.
        if (player == null || !this.nebula.isAlive() || player.getVehicle() != this.nebula || this.nebula.isDisabled()) {
            this.setDone();
            return;
        }

        // Actualizo la posición del sonido para que siga a la nave.
        this.x = (float)this.nebula.getX();
        this.y = (float)this.nebula.getY();
        this.z = (float)this.nebula.getZ();

        // Lógica para el volumen y el tono del motor.
        // La velocidad de la nave determinará qué tan "acelerado" suena el motor.
        float speed = (float)this.nebula.getVelocity().length();
        // El volumen sube gradualmente hasta 1.0f cuando la nave se mueve.
        // Si está quieta, el volumen baja a un ralentí suave.
        if (speed > 0.01f) {
            this.volume = MathHelper.lerp(0.05f, this.volume, 1.0f);
        } else {
            this.volume = MathHelper.lerp(0.1f, this.volume, 0.3f); // Volumen de ralentí
        }
        this.volume = MathHelper.clamp(this.volume, 0.0f, 1.0f);

        // El tono (pitch) aumenta con la velocidad para simular la aceleración.
        this.pitch = MathHelper.clamp(0.8f + speed * 0.2f, 0.8f, 1.5f);
    }
}