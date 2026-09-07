#version 150

in vec4 vertexColor;
in vec3 viewPosition;
in vec3 effectPosition;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

float stableNoise(vec3 point) {
    return fract(sin(dot(floor(point * 5.0),
            vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }

    vec3 derivativeNormal = cross(dFdx(viewPosition), dFdy(viewPosition));
    vec3 normal = derivativeNormal / max(length(derivativeNormal), 0.00001);
    vec3 viewDirection = normalize(-viewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDirection)), 1.7);
    float layerCoordinate = fract(effectPosition.y * 0.42 - GameTime * 1800.0);
    float depositionBand = 1.0 - smoothstep(0.035, 0.18,
            abs(layerCoordinate - 0.5));
    float depositionCore = pow(depositionBand, 3.0);
    float granulation = stableNoise(effectPosition
            + vec3(0.0, GameTime * 720.0, 0.0));
    vec2 cellCoordinate = abs(fract(effectPosition.xz * 1.35) - 0.5);
    float cellEdge = smoothstep(0.38, 0.49,
            max(cellCoordinate.x, cellCoordinate.y));
    float energy = 0.24 + depositionBand * 0.42
            + depositionCore * 0.34 + granulation * 0.055;
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 coldTint = vec3(0.20, 0.76, 1.08) * max(luminance, 0.24);
    vec3 hotTint = vec3(1.30, 0.72, 0.24) * max(luminance, 0.24);
    vec3 coldMatter = mix(color.rgb, coldTint, 0.24);
    vec3 hotMatter = mix(color.rgb, hotTint, 0.30);
    vec3 hotEdge = mix(coldMatter, hotMatter,
            depositionBand * 0.72 + depositionCore * 0.28);
    vec3 emission = hotEdge * energy
            + normalize(max(color.rgb, vec3(0.001))) * fresnel * 0.38
            + mix(coldTint, hotTint, depositionBand) * cellEdge * 0.085;
    emission = emission / (vec3(1.0) + emission * 0.12);
    float alphaPulse = 0.58 + depositionBand * 0.22
            + fresnel * 0.14 + cellEdge * 0.035;
    fragColor = vec4(emission, color.a * alphaPulse);
}
