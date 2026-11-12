#version 150

// Scales the half-resolution rays up to the frame. Bilinear reads, steered along edges the way simple FXAA does, so
// the noise and the stair steps of the small target blur along shafts instead of across them.

uniform sampler2D RaySampler;
uniform vec2 RayTexel;  // one texel of RaySampler

in vec2 texCoord;
out vec4 fragColor;

const float REDUCE_MIN = 1.0 / 128.0;
const float REDUCE_MUL = 1.0 / 8.0;
const float SPAN_MAX = 4.0;
const vec3 LUMA = vec3(0.299, 0.587, 0.114);

vec3 at(vec2 uv) {
    return texture(RaySampler, uv).rgb;
}

void main() {
    vec3 rgbNW = at(texCoord + vec2(-0.5, -0.5) * RayTexel);
    vec3 rgbNE = at(texCoord + vec2(0.5, -0.5) * RayTexel);
    vec3 rgbSW = at(texCoord + vec2(-0.5, 0.5) * RayTexel);
    vec3 rgbSE = at(texCoord + vec2(0.5, 0.5) * RayTexel);
    vec3 rgbM = at(texCoord);

    float lumaNW = dot(rgbNW, LUMA);
    float lumaNE = dot(rgbNE, LUMA);
    float lumaSW = dot(rgbSW, LUMA);
    float lumaSE = dot(rgbSE, LUMA);
    float lumaM = dot(rgbM, LUMA);
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir = vec2(-((lumaNW + lumaNE) - (lumaSW + lumaSE)), (lumaNW + lumaSW) - (lumaNE + lumaSE));
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * REDUCE_MUL, REDUCE_MIN * lumaMax);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce + 1e-6);
    dir = clamp(dir * rcpDirMin, vec2(-SPAN_MAX), vec2(SPAN_MAX)) * RayTexel;

    vec3 rgbA = 0.5 * (at(texCoord + dir * (1.0 / 3.0 - 0.5)) + at(texCoord + dir * (2.0 / 3.0 - 0.5)));
    vec3 rgbB = rgbA * 0.5 + 0.25 * (at(texCoord - dir * 0.5) + at(texCoord + dir * 0.5));
    float lumaB = dot(rgbB, LUMA);

    fragColor = vec4((lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB, 1.0);
}
