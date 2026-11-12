#version 150

// One sun's lens flare. Ported from Sunbathing Godrays (MehVahdJukaar, MIT): the sprite centered on the sun, faded by
// how much of the sun is open sky.

uniform sampler2D DepthSampler;
uniform sampler2D LensFlare;    // grayscale "nova" sprite, CC0 via OpenGameArt

uniform vec2 LightPos;          // screen uv
uniform vec3 FlareColor;        // tint, already scaled by strength and the screen-edge fade
uniform float Aspect;
uniform vec2 DepthTexel;

in vec2 texCoord;
out vec4 fragColor;

const float FLARE_HALF = 0.35;  // sprite half-size, in screen heights

float skyAt(vec2 uv) {
    return step(0.999999, texture(DepthSampler, clamp(uv, 0.0, 1.0)).r);
}

void main() {
    // 5 taps so the flare fades smoothly as terrain crosses the sun.
    vec2 tap = 3.0 * DepthTexel;
    float visible = (skyAt(LightPos)
                   + skyAt(LightPos + vec2(tap.x, 0.0))
                   + skyAt(LightPos - vec2(tap.x, 0.0))
                   + skyAt(LightPos + vec2(0.0, tap.y))
                   + skyAt(LightPos - vec2(0.0, tap.y))) * 0.2;

    vec2 fromSun = texCoord - LightPos;
    fromSun.x *= Aspect;
    vec2 flareUV = fromSun / (2.0 * FLARE_HALF) + 0.5;

    float flare = 0.0;
    if (visible > 0.0 && all(greaterThanEqual(flareUV, vec2(0.0))) && all(lessThanEqual(flareUV, vec2(1.0)))) {
        flare = texture(LensFlare, flareUV).r;
    }
    fragColor = vec4(FlareColor * flare * visible, 1.0);
}
