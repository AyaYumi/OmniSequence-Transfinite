#version 150

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 InverseProjection;
uniform mat4 InverseView;
uniform vec2 SceneSize;
uniform vec3 RayOrigin;
uniform vec3 CameraFrac;
// Scalar packing uses Minecraft's float-array uniform upload (glUniform1fv).
uniform float LightPos[96];
uniform int LightCount;
uniform float LightRadius;
uniform float BoundaryWidth;
uniform float SimulationEnabled;
// Zero while the player is inside any light pool, one only outside every pool.
uniform float RevealWireframe;
out vec4 fragColor;

const int MAX_LIGHTS = 32;
const vec3 PALE_GREEN = vec3(0.53, 0.90, 0.66);
const vec3 DARK_SKY = vec3(0.0008, 0.0020, 0.0014);
const vec3 DARK_SURFACE = vec3(0.0015, 0.0035, 0.0025);

vec3 viewPoint(vec2 uv, float depth) {
    vec4 point = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}

vec3 relativePoint(vec2 uv, float depth) {
    return (InverseView * vec4(viewPoint(uv, depth), 1.0)).xyz;
}

bool isSky(float depth) {
    // Cleared depth has no visible surface. Keep distant terrain with depth below 1.
    return depth >= 1.0;
}

float lightCoverage(vec3 point, out float nearest) {
    float nearestSquared = 1e20;
    for (int index = 0; index < MAX_LIGHTS; index++) {
        if (index >= LightCount) break;
        vec3 center = vec3(LightPos[index * 3], LightPos[index * 3 + 1], LightPos[index * 3 + 2]);
        vec3 delta = point - center;
        nearestSquared = min(nearestSquared, dot(delta, delta));
    }
    nearest = sqrt(nearestSquared);
    // Taking the nearest centre forms a union of spheres; overlapping pools do not add brightness.
    return 1.0 - smoothstep(LightRadius - BoundaryWidth, LightRadius, nearest);
}

vec3 shorterStep(vec3 before, vec3 after, bool beforeSky, bool afterSky) {
    if (beforeSky || dot(before, before) < 1e-16) return after;
    if (afterSky || dot(after, after) < 1e-16) return before;
    return dot(before, before) < dot(after, after) ? before : after;
}

float gridLine(float coordinate, float footprint) {
    float planeDistance = min(fract(coordinate), 1.0 - fract(coordinate));
    float halfWidth = max(0.006, footprint * 0.55);
    float antialias = max(0.0005, footprint * 0.5);
    float line = 1.0 - smoothstep(halfWidth - antialias, halfWidth + antialias, planeDistance);
    // Subpixel grids fade out instead of turning entire distant/grazing surfaces green.
    return line * (1.0 - smoothstep(0.25, 0.75, footprint));
}

float blockGrid(vec3 point, vec3 worldNormal) {
    // Integer camera coordinates are unnecessary for a periodic one-block grid.
    // Adding only the fractional part keeps it stable even near the world border.
    vec3 coordinate = point + CameraFrac;
    vec3 footprint = fwidth(coordinate);
    vec3 lines = vec3(gridLine(coordinate.x, footprint.x),
                      gridLine(coordinate.y, footprint.y),
                      gridLine(coordinate.z, footprint.z));
    vec3 weights = pow(abs(worldNormal), vec3(8.0));
    weights /= max(weights.x + weights.y + weights.z, 1e-8);
    // Exclude the coordinate perpendicular to each block face: an integer face
    // coordinate is constant over the face and must never become a filled grid line.
    return dot(weights, vec3(max(lines.y, lines.z), max(lines.x, lines.z), max(lines.x, lines.y)));
}

float depthSilhouette(vec3 delta, vec3 normal, bool neighborSky, float tolerance) {
    if (neighborSky) return 1.0;
    // A sloping plane has a depth gradient but stays on the same tangent plane.
    // Only discontinuities/creases should count as an outline, not that gradient.
    float planeDistance = abs(dot(delta, normal));
    return smoothstep(tolerance, tolerance * 3.0, planeDistance);
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    float depth = texture(SceneDepth, uv).r;
    vec4 scene = texture(SceneColor, uv);
    vec3 viewPosition = viewPoint(uv, depth);
    vec3 point = (InverseView * vec4(viewPosition, 1.0)).xyz;
    float nearest;
    float coverage = lightCoverage(point, nearest);

    if (SimulationEnabled < 0.5) {
        if (isSky(depth)) {
            fragColor = scene;
            return;
        }
        float attenuation = 1.0 - smoothstep(0.0, LightRadius, nearest);
        float luminance = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
        float darkness = 1.0 - smoothstep(0.04, 0.55, luminance);
        // A modest local lift also reveals black pixels without washing out bright surfaces.
        vec3 glow = PALE_GREEN * (0.04 + 0.15 * darkness) * attenuation * coverage;
        vec3 lit = scene.rgb * (1.0 + 0.45 * darkness * attenuation * coverage) + glow;
        fragColor = vec4(lit, scene.a);
        return;
    }

    vec2 pixel = 1.0 / SceneSize;
    vec2 firstPixel = pixel * 0.5;
    vec2 lastPixel = 1.0 - firstPixel;
    vec2 leftUV = clamp(uv - vec2(pixel.x, 0.0), firstPixel, lastPixel);
    vec2 rightUV = clamp(uv + vec2(pixel.x, 0.0), firstPixel, lastPixel);
    vec2 downUV = clamp(uv - vec2(0.0, pixel.y), firstPixel, lastPixel);
    vec2 upUV = clamp(uv + vec2(0.0, pixel.y), firstPixel, lastPixel);
    float leftDepth = texture(SceneDepth, leftUV).r;
    float rightDepth = texture(SceneDepth, rightUV).r;
    float downDepth = texture(SceneDepth, downUV).r;
    float upDepth = texture(SceneDepth, upUV).r;
    // Substitute centre depth for sky taps so far-plane unprojection cannot spoil normals.
    vec3 left = relativePoint(leftUV, isSky(leftDepth) ? depth : leftDepth);
    vec3 right = relativePoint(rightUV, isSky(rightDepth) ? depth : rightDepth);
    vec3 down = relativePoint(downUV, isSky(downDepth) ? depth : downDepth);
    vec3 up = relativePoint(upUV, isSky(upDepth) ? depth : upDepth);
    // Choose the side on the current surface, rather than crossing a depth discontinuity.
    vec3 dx = shorterStep(point - left, right - point, isSky(leftDepth), isSky(rightDepth));
    vec3 dy = shorterStep(point - down, up - point, isSky(downDepth), isSky(upDepth));
    vec3 rawNormal = cross(dx, dy);
    float normalLength = length(rawNormal);
    vec3 normal = normalLength > 1e-12 ? rawNormal / normalLength
            : normalize(mat3(InverseView) * vec3(0.0, 0.0, 1.0));

    // Evaluate derivatives for all fragments in the simulation pass, before any sky return.
    float normalEdge = smoothstep(0.15, 0.65, length(fwidth(normal)));
    float grid = blockGrid(point, normal);
    float eyeDistance = length(viewPosition - RayOrigin);
    float tolerance = max(0.02, eyeDistance * 0.0003 + min(length(dx), length(dy)) * 0.35);
    float depthEdge = max(
            max(depthSilhouette(left - point, normal, isSky(leftDepth), tolerance),
                depthSilhouette(right - point, normal, isSky(rightDepth), tolerance)),
            max(depthSilhouette(down - point, normal, isSky(downDepth), tolerance),
                depthSilhouette(up - point, normal, isSky(upDepth), tolerance)));

    if (isSky(depth)) {
        fragColor = vec4(DARK_SKY * RevealWireframe, scene.a);
        return;
    }
    float outline = max(depthEdge, normalEdge);
    // Inside a light pool, hide every unlit surface in pure black. Only an
    // observer outside all pools can see the simulation's grid and contours.
    vec3 simulated = (DARK_SURFACE + PALE_GREEN * max(grid * 0.25, outline * 0.48)) * RevealWireframe;
    float attenuation = 1.0 - smoothstep(0.0, LightRadius, nearest);
    float luminance = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    float darkness = 1.0 - smoothstep(0.04, 0.55, luminance);
    vec3 litScene = scene.rgb * (1.0 + 0.45 * darkness * attenuation)
            + PALE_GREEN * (0.04 + 0.15 * darkness) * attenuation;
    fragColor = vec4(mix(simulated, litScene, coverage), scene.a);
}
