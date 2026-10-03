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
uniform float HoleKind;
out vec4 fragColor;

vec3 viewPoint(vec2 uv, float depth) {
    vec4 p = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float sceneDistance(vec2 uv) {
    float depth = texture(SceneDepth, uv).r;
    return depth >= 0.999999 ? 1e6 : length(viewPoint(uv, depth));
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec3 ray = normalize(viewPoint(uv, 0.5));
    vec3 coreColor = vec3(HoleKind);
    float centerDistance2 = dot(CoreCenter, CoreCenter);
    if (centerDistance2 < CoreRadius * CoreRadius) {
        fragColor = vec4(coreColor, 1.0);
        return;
    }
    float along = dot(ray, CoreCenter);
    float impact2 = max(0.0, centerDistance2 - along * along);
    if (along <= 0.0 || impact2 >= FieldRadius * FieldRadius) discard;
    float impact = sqrt(impact2);
    float fieldNear = max(0.0, along - sqrt(FieldRadius * FieldRadius - impact2));
    float distance = sceneDistance(uv);
    if (distance < fieldNear) discard;
    float q = impact / CoreRadius;
    float aa = max(fwidth(q), 0.001);
    float core = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, q);
    if (core > 0.0) {
        float coreNear = max(0.0, along - sqrt(max(0.0, CoreRadius * CoreRadius - impact2)));
        if (distance < coreNear) discard;
    }

    vec4 projected = LensProjection * vec4(CoreCenter, 1.0);
    vec2 centerUV = projected.xy / projected.w * 0.5 + 0.5;
    vec2 radial = uv - centerUV;
    float gap = max(q - 1.0, 0.0);
    float fade = 1.0 - smoothstep(1.0, FieldRadius / CoreRadius, q);
    float flow = sin(gap * 9.0 + LensTime * (HoleKind > 0.5 ? -0.12 : 0.12));
    float strength = exp(-gap * 1.3) * fade;
    vec2 tangent = vec2(-radial.y, radial.x);
    vec2 offset = radial * (HoleKind > 0.5 ? -0.72 : 0.78) * strength;
    offset += radial * flow * 0.035 * fade;
    if (HoleKind < 0.5) offset += tangent * 0.19 * strength;
    float pixels = length(offset * SceneSize);
    offset *= min(1.0, SceneSize.y * 0.12 / max(pixels, 0.001));
    vec2 sampleUV = clamp(uv + offset, 0.5 / SceneSize, 1.0 - 0.5 / SceneSize);
    for (int i = 0; i < 5; i++) {
        if (sceneDistance(sampleUV) >= fieldNear) break;
        offset *= 0.5;
        sampleUV = clamp(uv + offset, 0.5 / SceneSize, 1.0 - 0.5 / SceneSize);
    }
    vec3 color = texture(SceneColor, sampleUV).rgb;
    if (HoleKind > 0.5) {
        float halo = (0.72 * exp(-gap * 8.0) + 0.08 * exp(-gap * 2.0)) * fade;
        color = mix(color, vec3(1.0), clamp(halo, 0.0, 1.0));
    }
    fragColor = vec4(mix(color, coreColor, core), 1.0);
}
