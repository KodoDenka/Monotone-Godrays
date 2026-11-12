package dev.manasnow.monotonegodrays.api;

import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Where other mods add their own suns and moons. */
public final class MonotoneGodraysApi {

    private static final List<GodrayLightProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MonotoneGodraysApi() {}

    /** Adds a provider. Providers registered later are asked first. */
    public static void register(GodrayLightProvider provider) {
        PROVIDERS.addFirst(provider);
    }

    /**
     * The rotation vanilla's sky turns to before drawing the sun at +Y and the moon at -Y, for a sky angle of
     * {@code skyAngle} (0-1, from {@code World.getSkyAngle}). Turn on from it to place more lights the same way.
     */
    public static Quaternionf skyFrame(float skyAngle) {
        return new Quaternionf().rotateY((float) Math.toRadians(-90)).rotateX(skyAngle * (float) (Math.PI * 2));
    }

    /** Asks each provider in turn for {@code world}'s lights; returns the one that handled it, or null. */
    @Nullable
    public static GodrayLightProvider collect(ClientWorld world, float tickDelta, Consumer<GodrayLight> out) {
        for (GodrayLightProvider provider : PROVIDERS) {
            if (provider.lights(world, tickDelta, out)) return provider;
        }
        return null;
    }
}
