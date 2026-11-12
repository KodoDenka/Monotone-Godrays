package dev.manasnow.monotonegodrays.api;

import org.joml.Vector3fc;

/**
 * One sun or moon that casts rays this frame.
 *
 * @param kind     a sun's rays fade out below the horizon; a moon's also fade out while any sun is up
 * @param x        direction to the light in the sky's frame (+Y up), the way the sky draws it; need not be unit length
 * @param red      ray color, 0-1 per channel
 * @param size     half-width of the light's quad at distance 100, as the sky draws it (vanilla's sun is 30, its moon 20)
 * @param strength scales the rays, 0 for none (a new moon)
 */
public record GodrayLight(Kind kind, float x, float y, float z, float red, float green, float blue, float size, float strength) {

    public enum Kind { SUN, MOON }

    /** A light toward {@code direction} in the 0xRRGGBB color {@code rgb}. */
    public static GodrayLight of(Kind kind, Vector3fc direction, int rgb, float size, float strength) {
        return new GodrayLight(kind, direction.x(), direction.y(), direction.z(),
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, size, strength);
    }
}
