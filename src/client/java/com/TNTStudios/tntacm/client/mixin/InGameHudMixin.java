package com.TNTStudios.tntacm.client.mixin;

import com.TNTStudios.tntacm.client.ShipViewController;
import com.TNTStudios.tntacm.client.hud.ShipHudRenderer; // Importamos nuestro renderer
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /**
     * Este Mixin ahora reemplaza completamente el HUD de vanilla con el HUD de la nave
     * cuando el jugador está en la vista correspondiente.
     *
     * - Se inyecta en "HEAD" para máxima eficiencia, evitando cualquier renderizado innecesario.
     * - Llama a nuestro `ShipHudRenderer` para dibujar la interfaz personalizada.
     * - Cancela el método original para prevenir que el HUD de vanilla se dibuje.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tntacm$onRenderHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ShipViewController.isInShipView) {
            // Dibuja nuestro HUD personalizado
            ShipHudRenderer.render(context, tickDelta);
            // Cancela el renderizado del HUD de vanilla
            ci.cancel();
        }
    }
}