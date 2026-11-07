#version 330

// SA-04 Proxima's film, its space and Earth shots (client/proxima/ProximaCosmos): a scene of its own, in its own units
// (the star's radius is 1, Earth's is 1), drawn over the world while those shots play.
#moj_import <minecraft:projection.glsl>
layout(std140) uniform ProximaScene {
    mat4 ModelViewMat;
    vec4 CameraTime;
    vec4 SunMode;
    vec4 Param;
};
#define CamPos CameraTime.xyz
#define Time CameraTime.w
#define SunDir SunMode.xyz
#define Mode SunMode.w

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out vec3 pos;
out vec2 uv;
out vec4 color;
out vec3 normal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    pos = Position;
    uv = UV0;
    color = Color;
    normal = Normal;
}
