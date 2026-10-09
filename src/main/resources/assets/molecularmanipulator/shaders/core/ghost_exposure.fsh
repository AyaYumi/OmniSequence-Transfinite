#version 150
uniform sampler2D SceneColor;
uniform vec2 SceneSize;
uniform float Exposure;
uniform float ExposureTime;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1, 0)), f.x),
               mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), f.x), f.y);
}

float cloud(vec2 p) {
    return .57 * noise(p) + .28 * noise(p * 2.03 + 7.7) + .15 * noise(p * 4.11 + 19.1);
}

float jagged(float x, float seed) {
    float cell = floor(x);
    return mix(hash(vec2(cell, seed)), hash(vec2(cell + 1.0, seed)), fract(x));
}

// Each fragment has its own direction, length, unequal corners and torn edges.
// Coordinates use screen height so resizing does not stretch the ribbon widths.
void sheet(vec2 uv, vec2 origin, float angle, float span, float width, float seed, float t,
           inout float violet, inout float rim, inout float wake) {
    origin += vec2(.013 * sin(t * .19 + seed * 1.7), .017 * cos(t * .15 + seed));
    angle += .025 * sin(t * .13 + seed * 2.3);
    vec2 direction = vec2(cos(angle), sin(angle));
    vec2 delta = (uv - origin) * vec2(SceneSize.x / SceneSize.y, 1.0);
    float x = dot(delta, direction) / span;
    float y = dot(delta, vec2(-direction.y, direction.x));
    float k1 = .12 + .16 * hash(vec2(seed, 2));
    float k2 = .39 + .17 * hash(vec2(seed, 3));
    float k3 = .68 + .14 * hash(vec2(seed, 4));
    float v1 = (hash(vec2(seed, 5)) - .5) * .15;
    float v2 = (hash(vec2(seed, 6)) - .5) * .19;
    float v3 = (hash(vec2(seed, 7)) - .5) * .13;
    float tip = (hash(vec2(seed, 8)) - .5) * .12;
    float path = v1 * clamp(x / k1, 0.0, 1.0)
               + (v2 - v1) * clamp((x - k1) / (k2 - k1), 0.0, 1.0)
               + (v3 - v2) * clamp((x - k2) / (k3 - k2), 0.0, 1.0)
               + (tip - v3) * clamp((x - k3) / (1.0 - k3), 0.0, 1.0);
    float taper = pow(max(0.0, 1.0 - x), .65);
    float breadth = width * taper * (.55 + .9 * jagged(x * 5.0, seed + 9.0));
    float upper = breadth * (.6 + .65 * jagged(x * 19.0, seed + 13.0));
    float lower = breadth * (.4 + .65 * jagged(x * 13.0, seed + 17.0));
    float d = y - path;
    float aa = max(fwidth(d), 1.0 / SceneSize.y);
    float reach = smoothstep(0.0, .025, x) * (1.0 - smoothstep(.985, 1.0, x));
    float body = smoothstep(-lower - aa, -lower, d)
               * (1.0 - smoothstep(upper, upper + aa, d)) * reach;
    violet = max(violet, body * (.8 + .2 * jagged(x * 7.0, seed + 21.0)));
    rim = max(rim, body * smoothstep(upper - aa * 2.0, upper, d));
    float trail = exp(-pow((d - upper - .022) / (.032 + breadth * 1.5), 2.0));
    wake += trail * reach * (.35 + .65 * cloud(vec2(x * 8.0 - t * .11, d * 19.0 + seed)));
}

// Warped, overlapping wisps make an uneven luminous patch, without a circular core.
vec3 bloom(vec2 uv, vec2 at, float angle, vec2 extent, float seed, float t) {
    at += vec2(.018 * sin(t * .11 + seed), .027 * cos(t * .16 + seed * 1.3));
    vec2 direction = vec2(cos(angle), sin(angle));
    vec2 delta = (uv - at) * vec2(SceneSize.x / SceneSize.y, 1.0);
    vec2 q = vec2(dot(delta, direction), dot(delta, vec2(-direction.y, direction.x)));
    vec2 drift = vec2(t * .06, -t * .035);
    q += vec2(cloud(uv * vec2(9.0, 6.0) + seed + drift) - .5,
              cloud(uv * vec2(6.0, 11.0) - seed - drift) - .5) * vec2(.12, .15);
    q /= extent;
    float vapor = cloud(uv * vec2(19.0, 13.0) + seed * 3.0 + drift);
    float envelope = exp(-dot(q, q) * 1.8);
    vec2 lobe = (q - vec2(.65, .2)) * vec2(.85, 1.4);
    float fragments = smoothstep(.25, .74, vapor);
    float core = (envelope + .5 * exp(-dot(lobe, lobe) * 2.6)) * (.3 + 1.1 * fragments);
    float halo = exp(-dot(q, q) * .48) * (.45 + .55 * vapor);
    return vec3(.015, .55, .42) * halo * .5 + vec3(.82, .91, .95) * core;
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec2 p = uv - .5;
    float t = ExposureTime;
    float edge = smoothstep(.12, .62, length(p * vec2(.95, 1.15)));
    float strength = clamp(Exposure, 0.0, 1.0);
    float violet = 0.0, rim = 0.0, wake = 0.0;
    sheet(uv, vec2(-.06, .94), -.32, 1.08, .035, 1.0, t, violet, rim, wake);
    sheet(uv, vec2(.04, .62), -.21, .68, .019, 4.7, t, violet, rim, wake);
    sheet(uv, vec2(-.03, .27), -.28, .91, .045, 8.3, t, violet, rim, wake);
    sheet(uv, vec2(.12, .07), .25, 1.03, .037, 2.6, t, violet, rim, wake);
    sheet(uv, vec2(.30, .22), .06, .47, .016, 7.2, t, violet, rim, wake);
    sheet(uv, vec2(1.04, .87), 3.39, .70, .026, 5.9, t, violet, rim, wake);
    sheet(uv, vec2(.98, .37), 2.72, .83, .040, 3.1, t, violet, rim, wake);
    sheet(uv, vec2(.68, -.02), .50, .59, .025, 9.8, t, violet, rim, wake);
    sheet(uv, vec2(.73, 1.03), 3.77, .58, .014, 6.4, t, violet, rim, wake);

    float vapor = cloud(uv * vec2(7.0, 4.0) + vec2(t * .055, -t * .037));
    float haze = (.08 + .38 * edge) * smoothstep(.27, .78, vapor);
    float green = (wake * (.35 + .5 * edge) + haze) * strength;
    vec2 ripple = vec2(sin(uv.y * 15.0 + t * .7), cos(uv.x * 12.0 - t * .6));
    vec2 sampleUv = clamp(uv + ripple * .003 * green, .002, .998);
    vec3 scene = texture(SceneColor, sampleUv).rgb;
    float split = .0014 * green;
    scene.r = texture(SceneColor, clamp(sampleUv + vec2(split, 0), .002, .998)).r;
    scene.b = texture(SceneColor, clamp(sampleUv - vec2(split, 0), .002, .998)).b;
    vec3 color = scene * mix(vec3(1.0), vec3(.72, .83, .93), strength * (.3 + .7 * edge));
    vec3 light = vec3(.015, .49, .39) * green;
    light += bloom(uv, vec2(.14, .72), -.35, vec2(.23, .077), 1.3, t) * .95 * strength;
    light += bloom(uv, vec2(.83, .85), .45, vec2(.19, .12), 4.6, t) * strength;
    light += bloom(uv, vec2(.28, .08), .18, vec2(.27, .068), 7.1, t) * .82 * strength;
    light += bloom(uv, vec2(.92, .22), -.62, vec2(.15, .09), 9.5, t) * .68 * strength;
    color += light;

    // Composite last: blooms cannot overwrite the tint, and its total opacity stays
    // between 14 and 24 percent. Most of the scene, including its texture contrast,
    // is transmitted through the blue-violet sheet even at maximum exposure.
    vec3 sheetColor = color * vec3(.84, .48, 1.02) + vec3(.035, .01, .13);
    float sheetOpacity = violet * strength * (.18 + .06 * edge);
    color = mix(color, sheetColor, sheetOpacity);
    color += vec3(.09, .035, .22) * rim * strength * .22;
    float vignette = smoothstep(.42, .73, length(p * vec2(1.0, 1.12)));
    color *= 1.0 - vignette * strength * .25;
    fragColor = vec4(color, 1.0);
}
