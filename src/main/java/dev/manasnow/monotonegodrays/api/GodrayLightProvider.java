package dev.manasnow.monotonegodrays.api;

import net.minecraft.client.world.ClientWorld;

import java.util.function.Consumer;

/** Supplies the suns and moons of the worlds a mod draws its own sky in. */
@FunctionalInterface
public interface GodrayLightProvider {

    /**
     * Adds {@code world}'s lights this frame to {@code out} and returns true, or returns false to leave the world to the
     * next provider (and in the end the vanilla sun and moon). Returning true with no lights turns rays off there.
     */
    boolean lights(ClientWorld world, float tickDelta, Consumer<GodrayLight> out);

    /**
     * The alpha the clouds in {@code world} are drawn at, if this provider handles it, or NaN for vanilla's 0.8.
     * Thicker clouds let less of the rays through.
     */
    default float cloudOpacity(ClientWorld world) {
        return Float.NaN;
    }
}
