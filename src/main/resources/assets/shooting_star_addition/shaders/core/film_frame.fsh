#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // World and post-processing passes can leave zero alpha even where their RGB is visible.
    fragColor = vec4(texture(Sampler0, texCoord0).rgb, 1.0) * vertexColor * ColorModulator;
}
