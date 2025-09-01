// src/main/java/com/TNTStudios/tntacm/sound/ModSounds.java
package com.TNTStudios.tntacm.sound;

import com.TNTStudios.tntacm.Tntacm;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    // 1. Defino los SoundEvents usando los identificadores de sounds.json.
    // Esto me da una referencia estática y segura para usarlos en el código.
    public static final SoundEvent ENTITY_NEBULA_LASER = registerSoundEvent("entity.nebula.laser");
    public static final SoundEvent ENTITY_NEBULA_RELOAD = registerSoundEvent("entity.nebula.reload");
    public static final SoundEvent ENTITY_NEBULA_DISABLED = registerSoundEvent("entity.nebula.disabled");
    public static final SoundEvent ENTITY_NEBULA_ENGINE = registerSoundEvent("entity.nebula.engine");


    // 2. Un método de ayuda para registrar los eventos de sonido de forma limpia.
    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = new Identifier(Tntacm.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    // 3. Este método lo llamaré desde la clase principal para que se ejecuten los registros.
    public static void registerSounds() {
        Tntacm.LOGGER.info("Registrando Sonidos para " + Tntacm.MOD_ID);
    }
}