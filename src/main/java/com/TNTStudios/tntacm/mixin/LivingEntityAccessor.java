// src/main/java/com/TNTStudios/tntacm/mixin/LivingEntityAccessor.java
package com.TNTStudios.tntacm.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean isJumpingInput();
}