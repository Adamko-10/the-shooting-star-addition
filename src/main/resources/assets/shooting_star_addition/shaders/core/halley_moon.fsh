#version 330

// SS-06 Luna's moon sphere (client/luna/render/MoonSphere): just the texture times the vertex colour (which already
// carries the sphere's own faked lighting, baked in on the CPU - see MoonSphere.vertex). No lightmap, no fog: this
// sphere is drawn pulled toward the camera at a faked distance while it's still far off, so vanilla fog (which reads
// the real distance) would wash it out long before it should fade; not referencing the Fog uniform here means it
// never does, on any backend, with or without a shader pack's own fog.
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    fragColor = vec4(tex.rgb * vertexColor.rgb * ColorModulator.rgb, tex.a * vertexColor.a * ColorModulator.a);
}
