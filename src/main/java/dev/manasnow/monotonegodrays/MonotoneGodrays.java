package dev.manasnow.monotonegodrays;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonotoneGodrays implements ClientModInitializer {
    public static final String MOD_ID = "monotone_godrays";
    public static final Logger LOGGER = LoggerFactory.getLogger("Monotone Godrays");

    @Override
    public void onInitializeClient() {
        GodraysConfig.register();
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(Identifier.of(MOD_ID, "godrays_mask"), VertexFormats.POSITION, p -> GodrayRenderer.maskProgram = p);
            context.register(Identifier.of(MOD_ID, "godrays_rays"), VertexFormats.POSITION, p -> GodrayRenderer.raysProgram = p);
            context.register(Identifier.of(MOD_ID, "godrays_flare"), VertexFormats.POSITION, p -> GodrayRenderer.flareProgram = p);
            context.register(Identifier.of(MOD_ID, "godrays_upscale"), VertexFormats.POSITION, p -> GodrayRenderer.upscaleProgram = p);
        });
        // After the world (translucent blocks and clouds included) and before the hand.
        WorldRenderEvents.END.register(GodrayRenderer::render);
    }
}
