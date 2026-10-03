#version 150
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 LensProjection;
uniform mat4 InverseProjection;
uniform vec2 SceneSize;
uniform vec3 CoreCenter;
uniform float CoreRadius;
uniform float FieldRadius;
uniform float LensTime;
out vec4 fragColor;

vec3 viewPoint(vec2 uv, float depth) {
    vec4 p = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
float sceneDistance(vec2 uv) {
    float depth = texture(SceneDepth, uv).r;
    return depth >= 0.999999 ? 1e6 : length(viewPoint(uv, depth));
}
bool backgroundFootprint(vec2 uv, float minimumDistance) {
    // Linear color filtering touches four texels. Reject the whole footprint if one is
    // foreground, otherwise its color can leak into a second, distorted silhouette.
    vec2 texel = 0.51 / SceneSize;
    return sceneDistance(uv + vec2(-texel.x, -texel.y)) >= minimumDistance
        && sceneDistance(uv + vec2( texel.x, -texel.y)) >= minimumDistance
        && sceneDistance(uv + vec2(-texel.x,  texel.y)) >= minimumDistance
        && sceneDistance(uv + vec2( texel.x,  texel.y)) >= minimumDistance;
}
void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec3 ray = normalize(viewPoint(uv, 0.5));
    if (dot(CoreCenter, CoreCenter) < CoreRadius * CoreRadius) {
        fragColor = vec4(1.0);
        return;
    }
    float along = dot(ray, CoreCenter);
    float impact2 = max(0.0, dot(CoreCenter, CoreCenter) - along * along);
    if (along <= 0.0 || impact2 >= FieldRadius * FieldRadius) discard;
    float impact = sqrt(impact2);
    float fieldNear = max(0.0, along - sqrt(FieldRadius * FieldRadius - impact2));
    float distance = sceneDistance(uv);
    // Geometry in front of the influence volume must remain pixel-for-pixel unchanged.
    if (distance < fieldNear) discard;
    float coreNear = max(0.0, along - sqrt(max(0.0, CoreRadius * CoreRadius - impact2)));
    // Keep the actual foreground pixel, rather than rejecting the white core with its depth
    // while fetching a different dark pixel through refraction (which creates black wires).
    if (distance < coreNear) discard;
    float fieldFade = 1.0 - smoothstep(CoreRadius, FieldRadius, impact);
    float q = impact / CoreRadius;
    vec4 clipCenter = LensProjection * vec4(CoreCenter, 1.0);
    vec2 centerUV = clipCenter.xy / clipCenter.w * 0.5 + 0.5;
    vec2 radial = uv - centerUV;
    // A smooth radial lens stretches real scenery around the white horizon. No shell tint,
    // screen-facing tiles, turbulent star surface or polar jets are involved.
    float strength = 0.86 / (q * q + 0.35) * fieldFade;
    strength *= 1.0 + 0.012 * sin(LensTime * 0.01570796);
    vec2 offset = -radial * min(strength, 0.72);
    float pixels = length(offset * SceneSize);
    offset *= min(1.0, SceneSize.y * 0.12 / max(pixels, 0.001));
    vec2 halfPixel = 0.5 / SceneSize;
    vec2 sampleUV = uv;
    for (int i = 0; i < 5; i++) {
        sampleUV = clamp(uv + offset, halfPixel, 1.0 - halfPixel);
        if (backgroundFootprint(sampleUV, coreNear)) break;
        offset *= 0.5;
        sampleUV = uv;
    }
    vec3 refracted = texture(SceneColor, sampleUV).rgb;
    float visible = 1.0;
    float aa = max(fwidth(q), 0.001);
    float core = (1.0 - smoothstep(1.0 - aa, 1.0 + aa, q)) * visible;
    float gap = max(q - 1.0, 0.0);
    float halo = (0.85 * exp(-gap * 9.0) + 0.075 * exp(-gap * 2.0)) * fieldFade * visible;
    vec3 color = mix(refracted, vec3(1.0), clamp(halo, 0.0, 1.0));
    color = mix(color, vec3(1.0), core);
    fragColor = vec4(color, 1.0);
}
