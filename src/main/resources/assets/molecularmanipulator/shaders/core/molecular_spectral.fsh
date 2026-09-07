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
    float fresnel = pow(1.0 - abs(dot(normal, viewDirection)), 1.8);
    float spectrum = 0.5 + 0.5 * sin(dot(effectPosition,
            vec3(1.71, 2.37, 1.13)) * 1.35 + GameTime * 2400.0);
    float wavefront = 0.5 + 0.5 * sin(length(effectPosition.xz) * 3.8
            - effectPosition.y * 1.9 - GameTime * 1800.0);
    float spectralLine = pow(spectrum, 3.4);
    float waveLine = pow(wavefront, 5.0);
    float envelope = 0.34 + spectrum * 0.44 + spectralLine * 0.38
            + waveLine * 0.24;
    vec3 coolPhase = color.rgb * vec3(0.54, 1.18, 1.42);
    vec3 hotPhase = color.rgb * vec3(1.34, 0.58, 1.18);
    vec3 spectralTint = mix(coolPhase, hotPhase, spectrum);
    vec3 emission = spectralTint * envelope
            + normalize(max(color.rgb, vec3(0.001))) * fresnel * 0.82;
    float alphaPulse = 0.66 + spectralLine * 0.22 + fresnel * 0.18;
    fragColor = vec4(emission, color.a * alphaPulse);
}
