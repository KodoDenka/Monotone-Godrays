#version 150

// One light's rays, at the rays target's resolution. Ported from Sunbathing Godrays (MehVahdJukaar, MIT): march from
// the pixel toward the light, adding the light's disc wherever the step lands on open sky.

uniform sampler2D DepthSampler;

uniform vec2 LightPos;      // screen uv
uniform float LightRadius;  // disc radius, in screen heights
uniform vec3 LightColor;    // already scaled by the light's strength
uniform float Samples;
uniform float Density;
uniform float Decay;
uniform float Aspect;
uniform vec2 RayTexel;

in vec2 texCoord;
out vec4 fragColor;

const float EXPOSURE = 0.25;
const float WEIGHT = 0.25;
const float GLOW = 0.4;

float sky(vec2 uv) {
    return step(0.999999, texture(DepthSampler, uv).r);
}

float interleavedGradientNoise(vec2 uv) {
    return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
}

float disc(vec2 uv) {
    vec2 d = uv - LightPos;
    d.x *= Aspect;
    float dist = length(d);
    float core = smoothstep(LightRadius, LightRadius * 0.8, dist);
    float glow = exp(-dist / (LightRadius * GLOW)) * GLOW;
    return core + glow;
}

void main() {
    // Only geometry shows rays. The upscale reads about a texel around each pixel, so a sky pixel is skipped only
    // when there's sky around it too.
    if (sky(texCoord) > 0.5) {
        vec2 o = RayTexel * 1.5;
        if (sky(texCoord + o) * sky(texCoord - o) * sky(texCoord + vec2(o.x, -o.y)) * sky(texCoord + vec2(-o.x, o.y)) > 0.5) {
            fragColor = vec4(0.0);
            return;
        }
    }

    int samples = max(1, int(Samples));
    vec2 delta = (texCoord - LightPos) * (Density / float(samples));
    vec2 coord = texCoord + delta * interleavedGradientNoise(gl_FragCoord.xy);

    float acc = 0.0;
    float decay = 1.0;
    for (int i = 0; i < samples; ++i) {
        coord -= delta;
        vec2 b0 = smoothstep(vec2(0.0), vec2(0.08), coord);
        vec2 b1 = smoothstep(vec2(1.0), vec2(0.92), coord);
        float border = b0.x * b0.y * b1.x * b1.y;
        acc += sky(coord) * disc(coord) * decay * WEIGHT * border;
        decay *= Decay;
    }

    fragColor = vec4(LightColor * acc * EXPOSURE, 1.0);
}
