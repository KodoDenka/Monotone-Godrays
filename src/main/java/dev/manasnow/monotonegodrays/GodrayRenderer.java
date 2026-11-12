package dev.manasnow.monotonegodrays;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.manasnow.monotonegodrays.api.GodrayLight;
import dev.manasnow.monotonegodrays.api.MonotoneGodraysApi;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the rays after the world. Each light's rays march at half resolution into {@code rays}, adding up, and are
 * scaled back up onto the world with an FXAA-style filter. The upscale quad sits just short of the far plane with the
 * depth test reversed, so only geometry gets rays (the sky already has its sun) and the edge stays full resolution.
 * Lens flares go to their own target and are added over everything.
 */
public final class GodrayRenderer {

    @Nullable
    static ShaderProgram raysProgram;
    @Nullable
    static ShaderProgram flareProgram;
    @Nullable
    static ShaderProgram upscaleProgram;

    /** Lights and flares drawn last frame, for tests. */
    public static int lastLights;
    public static int lastFlares;
    /** Tests set this to time the passes on the GPU; each frame then waits for the timer's result. */
    public static boolean measure;
    /** Time the passes took and frames timed, added up while {@link #measure} is on. */
    public static long measuredNanos;
    public static int measuredFrames;

    private static final int MAX_LIGHTS = 8;
    /** The sun and moon textures' disc as a share of their quad, so vanilla's sun matches Sunbathing's disc. */
    private static final float DISC_SHARE = 0.28F;
    /** Window depth 0.999999, Sunbathing's sky test: anything farther counts as sky. */
    private static final float SKY_DEPTH_NDC = 0.999998F;
    private static final float FLARE_STRENGTH = 0.6F;
    private static final float FLARE_TINT_R = 1.0F, FLARE_TINT_G = 0.93F, FLARE_TINT_B = 0.8F;
    /** How much of the sun's own color a flare takes on. */
    private static final float FLARE_SUN_SHARE = 0.35F;
    /** Rain past this fades the rays out entirely. */
    private static final float RAIN_CUTOFF = 0.2F;
    private static final Identifier LENS_FLARE = Identifier.of(MonotoneGodrays.MOD_ID, "textures/effect/lens_flare.png");

    private static SimpleFramebuffer rays;
    private static SimpleFramebuffer flares;
    private static final List<GodrayLight> frameLights = new ArrayList<>();

    private static int timerQuery;

    private static boolean irisChecked;
    private static Object irisApi;
    private static Method irisShaderPackInUse;

    private GodrayRenderer() {}

    /** A light on screen: its position, disc radius (in screen heights), ray color and flare color. */
    private record OnScreen(float u, float v, float radius, float r, float g, float b, float weight,
                            float flareR, float flareG, float flareB) {}

    static void render(WorldRenderContext context) {
        lastLights = 0;
        lastFlares = 0;
        GodraysConfig config = GodraysConfig.get();
        ShaderProgram raysShader = raysProgram, flareShader = flareProgram, upscaleShader = upscaleProgram;
        if (!config.enabled.get() || raysShader == null || flareShader == null || upscaleShader == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = context.world();
        // The game renderer's camera, not the context's: Sable swaps in a stand-in there that is empty by now.
        if (world == null || blocked(client, client.gameRenderer.getCamera())) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        float clear = 1 - MathHelper.clamp(world.getRainGradient(tickDelta) / RAIN_CUTOFF, 0, 1);
        if (clear <= 0) return;

        frameLights.clear();
        if (!MonotoneGodraysApi.collect(world, tickDelta, frameLights::add)) {
            VanillaLights.lights(world, tickDelta, frameLights::add);
        }
        if (frameLights.isEmpty()) return;

        // Moons fade in as the highest sun sets.
        float day = 0;
        for (GodrayLight light : frameLights) {
            if (light.kind() == GodrayLight.Kind.SUN) day = Math.max(day, aboveHorizon(light));
        }

        Framebuffer main = client.getFramebuffer();
        Matrix4f view = context.positionMatrix();
        Matrix4f projection = context.projectionMatrix();
        float flareStrength = config.lensFlare.get() * FLARE_STRENGTH;
        List<OnScreen> onScreen = new ArrayList<>();
        boolean anyFlare = false;
        for (GodrayLight light : frameLights) {
            boolean sun = light.kind() == GodrayLight.Kind.SUN;
            float weight = aboveHorizon(light) * light.strength() * clear * (sun ? 1 : 1 - day);
            if (weight <= 0) continue;
            Vector4f clip = projection.transform(view.transform(new Vector4f(light.x(), light.y(), light.z(), 0)));
            if (clip.w <= 1e-4F) continue;
            float u = clip.x / clip.w * 0.5F + 0.5F, v = clip.y / clip.w * 0.5F + 0.5F;
            // Sunbathing's fade: full near the middle, gone a screen and a half out.
            float screenFade = 1 - smoothstep(0.2F, 1.5F, (float) Math.hypot(u - 0.5F, v - 0.5F));
            float strength = weight * screenFade * (sun ? config.sunIntensity.get() : config.moonIntensity.get());

            float flare = 0;
            if (sun && flareStrength > 0) {
                flare = weight * flareStrength * smoothstep(0, 0.25F, u) * (1 - smoothstep(0.75F, 1, u))
                        * smoothstep(0, 0.25F, v) * (1 - smoothstep(0.75F, 1, v));
            }
            if (strength <= 1e-3F && flare <= 1e-3F) continue;
            anyFlare |= flare > 1e-3F;
            float max = Math.max(1e-3F, Math.max(light.red(), Math.max(light.green(), light.blue())));
            float radius = light.size() / 100 * DISC_SHARE * projection.m11() * 0.5F;
            onScreen.add(new OnScreen(u, v, radius, light.red() * strength, light.green() * strength, light.blue() * strength, strength,
                    flare * MathHelper.lerp(FLARE_SUN_SHARE, FLARE_TINT_R, light.red() / max),
                    flare * MathHelper.lerp(FLARE_SUN_SHARE, FLARE_TINT_G, light.green() / max),
                    flare * MathHelper.lerp(FLARE_SUN_SHARE, FLARE_TINT_B, light.blue() / max)));
        }
        if (onScreen.isEmpty()) return;
        onScreen.sort(Comparator.comparingDouble(OnScreen::weight).reversed());
        if (onScreen.size() > MAX_LIGHTS) onScreen.subList(MAX_LIGHTS, onScreen.size()).clear();

        int divisor = config.fullResolution.get() ? 1 : 2;
        int width = Math.max(1, main.textureWidth / divisor), height = Math.max(1, main.textureHeight / divisor);
        float aspect = main.textureWidth / (float) main.textureHeight;
        int depth = main.getDepthAttachment();

        if (measure) {
            if (timerQuery == 0) timerQuery = GL15.glGenQueries();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, timerQuery);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);

        rays = target(rays, width, height);
        rays.beginWrite(true);
        raysShader.addSampler("DepthSampler", depth);
        uniform(raysShader, "Samples", config.quality.get().floatValue());
        uniform(raysShader, "Density", config.density.get());
        uniform(raysShader, "Decay", config.decay.get());
        uniform(raysShader, "Aspect", aspect);
        uniform(raysShader, "RayTexel", 1F / width, 1F / height);
        for (OnScreen light : onScreen) {
            if (light.weight() <= 1e-3F) continue;
            uniform(raysShader, "LightPos", light.u(), light.v());
            uniform(raysShader, "LightRadius", light.radius());
            uniform(raysShader, "LightColor", light.r(), light.g(), light.b());
            fullscreen(raysShader, 0);
            lastLights++;
        }

        if (anyFlare) {
            flares = target(flares, width, height);
            flares.beginWrite(true);
            AbstractTexture sprite = client.getTextureManager().getTexture(LENS_FLARE);
            sprite.setFilter(true, false);
            flareShader.addSampler("DepthSampler", depth);
            flareShader.addSampler("LensFlare", sprite.getGlId());
            uniform(flareShader, "Aspect", aspect);
            uniform(flareShader, "DepthTexel", 1F / main.textureWidth, 1F / main.textureHeight);
            for (OnScreen light : onScreen) {
                if (light.flareR() + light.flareG() + light.flareB() <= 1e-3F) continue;
                uniform(flareShader, "LightPos", light.u(), light.v());
                uniform(flareShader, "FlareColor", light.flareR(), light.flareG(), light.flareB());
                fullscreen(flareShader, 0);
                lastFlares++;
            }
        }

        main.beginWrite(true);
        // Leave the frame's alpha as it was.
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        uniform(upscaleShader, "RayTexel", 1F / width, 1F / height);
        if (lastLights > 0) {
            upscaleShader.addSampler("RaySampler", rays);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_GREATER);
            fullscreen(upscaleShader, SKY_DEPTH_NDC);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableDepthTest();
        }
        if (lastFlares > 0) {
            upscaleShader.addSampler("RaySampler", flares);
            fullscreen(upscaleShader, 0);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (measure) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            measuredNanos += GL33.glGetQueryObjecti64(timerQuery, GL15.GL_QUERY_RESULT);
            measuredFrames++;
        }
    }

    /** Whether the player can't see the sky's light as rays: underwater, in lava or powder snow, blind, or with a shader pack. */
    private static boolean blocked(MinecraftClient client, Camera camera) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return true;
        if (client.player != null && (client.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || client.player.hasStatusEffect(StatusEffects.DARKNESS))) return true;
        return shaderPackInUse();
    }

    /** Iris draws its own sky light; its API is read by reflection so Iris stays optional. */
    private static boolean shaderPackInUse() {
        if (!irisChecked) {
            irisChecked = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException e) {
                    irisShaderPackInUse = null;
                }
            }
        }
        if (irisShaderPackInUse == null) return false;
        try {
            return (boolean) irisShaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 0 below 6 degrees under the horizon, 1 from about 12 above, as Sunbathing's crossfade. */
    private static float aboveHorizon(GodrayLight light) {
        float length = (float) Math.sqrt(light.x() * light.x() + light.y() * light.y() + light.z() * light.z());
        if (length <= 0) return 0;
        return smoothstep(-0.1F, 0.2F, light.y() / length);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** {@code target} cleared at this size, made or resized as needed, filtered linearly for the upscale. */
    private static SimpleFramebuffer target(@Nullable SimpleFramebuffer target, int width, int height) {
        if (target == null) {
            target = new SimpleFramebuffer(width, height, false, MinecraftClient.IS_SYSTEM_MAC);
            target.setTexFilter(GL11.GL_LINEAR);
        } else if (target.textureWidth != width || target.textureHeight != height) {
            target.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            target.setTexFilter(GL11.GL_LINEAR);
        }
        target.setClearColor(0, 0, 0, 0);
        target.clear(MinecraftClient.IS_SYSTEM_MAC);
        return target;
    }

    /** A quad over the whole target at clip depth {@code z}. */
    private static void fullscreen(ShaderProgram shader, float z) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(-1, -1, z);
        buffer.vertex(1, -1, z);
        buffer.vertex(1, 1, z);
        buffer.vertex(-1, 1, z);
        RenderSystem.setShader(() -> shader);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void uniform(ShaderProgram shader, String name, float... values) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(values);
    }
}
