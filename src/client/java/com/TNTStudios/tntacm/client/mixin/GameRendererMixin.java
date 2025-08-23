package com.TNTStudios.tntacm.client.mixin;

import com.TNTStudios.tntacm.client.ShipViewController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    //region Mixin: GameRenderer#renderHand
    /**
     * Inyecta código al inicio del método que renderiza la mano en primera persona.
     * Si el jugador está en la vista de la nave, cancela completamente el método.
     *
     * PERF: Usar @At("HEAD") y ci.cancel() es la forma más eficiente de anular
     * una operación, ya que previene la ejecución de todo el código del método original.
     */
    @Inject(
            method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void tntacm$onRenderHand(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (ShipViewController.isInShipView) {
            ci.cancel();
        }
    }
    //endregion
}