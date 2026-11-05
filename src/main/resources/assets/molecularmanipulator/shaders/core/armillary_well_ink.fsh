#version 150

#moj_import <fog.glsl>

in float vertexDistance;

in vec2 wellUV;
in vec4 vertexColor;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1, 0)), f.x),
               mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), f.x), f.y);
}

float inkNoise(vec2 p) {
    float value = 0.0, weight = 0.5;
    int octaves = vertexColor.a > 0.75 ? 4 : 2;
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) break;
        value += noise(p) * weight;
        p = mat2(1.60, 1.20, -1.20, 1.60) * p + vec2(17.1, 9.2);
        weight *= 0.5;
    }
    return value * (octaves == 4 ? 1.0666667 : 1.3333333);
}

void main() {
    vec2 p = wellUV * 2.0 - 1.0;
    float radius = length(p);
    // Keep a real clear central aperture and feather the well's inside edge.
    if (radius >= 1.0 || radius <= 0.09) discard;
    vec2 direction = p / radius;
    float angle = atan(p.y, p.x);

    // Vanilla GameTime wraps after 1200 seconds. All drift/advection periods
    // divide that clock, so the outward ink motion has no discontinuous reset.
    float time = GameTime * 1200.0;
    vec2 drift = vec2(cos(time * TAU / 120.0), sin(time * TAU / 120.0));
    float warp = inkNoise(p * 4.8 + drift * 0.45);
    float radialPhase = radius * 1.90 - time / 8.0 + (warp - 0.5) * 0.95;
    vec2 seep = direction * (0.30 * cos(radialPhase * TAU));
    float wet = inkNoise(p * 9.0 - seep + drift * 0.30);
    float distanceToFront = abs(fract(radialPhase) - 0.5);

    // Broad mottled blooms, not narrow geometric rings. Domain warping lets
    // each edge bleed into irregular cloudlets and radial capillary fibres.
    float width = 0.22 + wet * 0.10;
    float bloom = exp(-pow(distanceToFront / width, 2.0));
    float bleed = exp(-pow(distanceToFront / 0.30, 2.0)) * smoothstep(0.34, 0.76, wet);
    float fibrePhase = angle * 152.0 + wet * 16.0 + radius * 38.0;
    float fibres = pow(0.5 + 0.5 * sin(fibrePhase), 8.0) / (1.0 + fwidth(fibrePhase));
    float fray = fibres * exp(-pow((distanceToFront - 0.18) / 0.11, 2.0));
    float grain = vertexColor.a > 0.75 ? noise(p * 125.0 + drift * 2.0) : 0.5;
    float pigment = bloom * (0.18 + 0.82 * wet) + bleed * 0.48;
    pigment *= 0.72 + grain * 0.56;

    float envelope = smoothstep(0.10, 0.26, radius) * (1.0 - smoothstep(0.93, 1.0, radius));
    float inkAlpha = envelope * clamp(pigment * 0.70 + fray * 0.32 + wet * 0.085, 0.0, 0.76);
    float poolAlpha = envelope * 0.62 * (1.0 - smoothstep(0.23, 0.46, radius));
    float pigmentAlpha = max(inkAlpha, poolAlpha);
    float alpha = pigmentAlpha * linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (alpha < 0.003) discard;

    vec3 pigmentColor = vertexColor.rgb * (0.12 + wet * 0.36);
    vec3 wetLight = mix(vertexColor.rgb, vec3(0.87, 0.99, 1.0), 0.35);
    vec3 color = mix(pigmentColor, wetLight, clamp(bloom * 0.62 + fray * 0.80, 0.0, 1.0));
    color = mix(color, vertexColor.rgb * 0.045, poolAlpha / max(pigmentAlpha, 0.001) * 0.65);
    fragColor = vec4(color * ColorModulator.rgb, alpha * ColorModulator.a);
}
