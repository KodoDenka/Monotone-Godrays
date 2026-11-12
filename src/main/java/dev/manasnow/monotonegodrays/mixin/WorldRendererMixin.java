package dev.manasnow.monotonegodrays.mixin;

import dev.manasnow.monotonegodrays.GodrayRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the depth buffer just before the clouds are drawn, so the rays can tell cloud from terrain. In the caller, so
 * it runs before any mod that takes over {@code renderClouds} at its head (Planet System's cloud layers).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "render", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V"))
    private void monotoneGodrays$beforeClouds(CallbackInfo ci) {
        GodrayRenderer.beforeClouds();
    }
}
