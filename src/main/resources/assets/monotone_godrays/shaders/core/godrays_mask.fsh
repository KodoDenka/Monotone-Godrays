#version 150

// Red: how much of a light gets through at each pixel of the rays target, 1 for open sky, CloudLight for a cloud in
// front of the sky, 0 for terrain. Green: 1 where the frame shows sky, 0 on terrain and clouds, which get rays. The
// rays march over this small picture instead of two full-size depth buffers.

uniform sampler2D DepthSampler;
uniform sampler2D CloudlessDepth;  // depth before the clouds drew; DepthSampler again when there is no copy
uniform float CloudLight;          // share of the light a cloud lets through

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float sky = step(0.999999, texture(DepthSampler, texCoord).r);
    float open = sky > 0.5 ? 1.0 : CloudLight * step(0.999999, texture(CloudlessDepth, texCoord).r);
    fragColor = vec4(open, sky, 0.0, 1.0);
}
