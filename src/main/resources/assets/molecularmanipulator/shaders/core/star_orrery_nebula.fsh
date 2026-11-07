#version 150

#moj_import <fog.glsl>
#moj_import <molecularmanipulator:star_orrery_volume.glsl>

uniform vec2 MarchSize;
out vec4 fragColor;

void main() {
    fragColor = integrateNebula(gl_FragCoord.xy / MarchSize);
}
