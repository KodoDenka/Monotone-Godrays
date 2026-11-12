package dev.manasnow.monotonegodrays;

import dev.manasnow.monotonegodrays.api.GodrayLight;
import dev.manasnow.monotonegodrays.api.MonotoneGodraysApi;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.world.ClientWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

/** The vanilla sun and moon, in Sunbathing's warm sun and cool moon colors. */
final class VanillaLights {

    private static final int SUN_COLOR = 0xFFB34D;
    private static final int MOON_COLOR = 0x8099FF;

    private VanillaLights() {}

    static void lights(ClientWorld world, float tickDelta, Consumer<GodrayLight> out) {
        if (world.getDimensionEffects().getSkyType() != DimensionEffects.SkyType.NORMAL) return;
        Quaternionf frame = MonotoneGodraysApi.skyFrame(world.getSkyAngle(tickDelta));
        Vector3f sun = frame.transform(new Vector3f(0, 1, 0));
        out.accept(GodrayLight.of(GodrayLight.Kind.SUN, sun, SUN_COLOR, 30, 1));
        out.accept(GodrayLight.of(GodrayLight.Kind.MOON, sun.negate(), MOON_COLOR, 20, world.getMoonSize()));
    }
}
