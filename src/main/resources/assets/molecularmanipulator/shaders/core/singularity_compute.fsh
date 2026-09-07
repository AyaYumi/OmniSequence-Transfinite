#version 150

in vec4 vertexColor;
in vec3 viewPosition;
in vec3 effectPosition;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }

    vec3 derivativeNormal = cross(dFdx(viewPosition), dFdy(viewPosition));
    vec3 normal = derivativeNormal / max(length(derivativeNormal), 0.00001);
    vec3 viewDirection = normalize(-viewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDirection)), 1.9);
    vec3 shiftedPosition = effectPosition
            + vec3(0.0, GameTime * 920.0, GameTime * -540.0);
    vec3 cell = abs(fract(shiftedPosition * 0.44) - vec3(0.5));
    float circuitDistance = min(cell.x, min(cell.y, cell.z));
    float circuit = 1.0 - smoothstep(0.025, 0.105, circuitDistance);
    float clockPulse = 0.5 + 0.5 * sin(effectPosition.y * 2.2
            + effectPosition.x * 0.55 - effectPosition.z * 0.48
            - GameTime * 2600.0);
    float clockEdge = pow(clockPulse, 5.0);
    float energy = 0.28 + circuit * 0.48 + clockPulse * 0.22
            + clockEdge * 0.42;
    vec3 idleTint = color.rgb * vec3(0.58, 0.82, 1.20);
    vec3 activeTint = color.rgb * vec3(0.72, 1.28, 1.46);
    vec3 clockTint = mix(idleTint, activeTint,
            circuit * 0.55 + clockPulse * 0.45);
    vec3 emission = clockTint * energy
            + normalize(max(color.rgb, vec3(0.001))) * fresnel * 0.94;
    float alphaPulse = 0.58 + circuit * 0.16
            + clockEdge * 0.22 + fresnel * 0.18;
    fragColor = vec4(emission, color.a * alphaPulse);
}
