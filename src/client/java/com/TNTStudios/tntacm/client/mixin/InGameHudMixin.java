package com.TNTStudios.tntacm.client.mixin;

import com.TNTStudios.tntacm.client.ShipViewController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /**
     * Este Mixin oculta completamente la interfaz de usuario (HUD) cuando el jugador
     * está en la vista de la nave.
     *
     * - @At("HEAD"): Se inyecta al inicio del método `render`. Es la estrategia más
     * eficiente, ya que evita que se ejecute cualquier código de renderizado del HUD
     * si la condición se cumple.
     *
     * - cancellable = true: Permite llamar a `ci.cancel()` para detener la ejecución
     * del método original, ahorrando rendimiento.
     *
     * Esta implementación es robusta y altamente compatible, ya que es una técnica estándar
     * y no modifica la lógica interna del HUD, solo la previene.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tntacm$onRenderHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ShipViewController.isInShipView) {
            ci.cancel();
        }
    }
}