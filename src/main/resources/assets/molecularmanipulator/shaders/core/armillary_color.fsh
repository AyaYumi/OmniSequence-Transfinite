#version 150

#moj_import <fog.glsl>

in float vertexDistance;
in vec4 vertexColor;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (color.a <= 0.0) discard;
    fragColor = color;
}
