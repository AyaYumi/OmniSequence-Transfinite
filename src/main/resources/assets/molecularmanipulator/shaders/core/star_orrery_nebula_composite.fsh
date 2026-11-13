#version 150

#moj_import <fog.glsl>
#moj_import <molecularmanipulator:star_orrery_volume.glsl>

uniform sampler2D SceneColor;
uniform sampler2D VolumeColor;
uniform vec2 SceneSize;
uniform vec2 MarchSize;
// Inclusive texel centers of this machine's freshly written rectangle.
uniform vec4 MarchBounds;
uniform int IntegrateDirect;
out vec4 fragColor;

vec4 resolveVolume(vec2 uv) {
    if (IntegrateDirect != 0) return integrateNebula(uv);
    vec2 position = uv * MarchSize - 0.5;
    vec2 base = floor(position), fraction = fract(position);
    float surface = surfaceDistance(uv);
    float tolerance = max(0.025, min(surface, 1000.0) * 0.002);
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 texel = clamp(base + vec2(x,y), MarchBounds.xy, MarchBounds.zw);
        vec2 sampleUV = (texel + 0.5) / MarchSize;
        float difference = abs(surfaceDistance(sampleUV) - surface);
        float weight = (x == 0 ? 1.0-fraction.x : fraction.x)
                * (y == 0 ? 1.0-fraction.y : fraction.y);
        // A foreground edge must not borrow radiance/transmittance from the other side.
        weight *= exp2(-8.0 * difference / tolerance);
        sum += texture(VolumeColor,sampleUV) * weight;
        total += weight;
    }
    // Thin foreground geometry may have no matching half-resolution sample. Integrate
    // only those pixels at native resolution rather than leaking light or erasing it.
    return total > 0.01 ? sum / total : integrateNebula(uv);
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec4 volume = resolveVolume(uv);
    if (volume.a > 0.999) discard;
    vec3 background = texture(SceneColor,uv).rgb;
    fragColor = vec4(background*volume.a+volume.rgb,1.0);
}
