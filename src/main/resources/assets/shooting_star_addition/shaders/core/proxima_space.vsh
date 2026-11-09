#version 330
#extension GL_ARB_separate_shader_objects : require

// SA-04 Proxima's film, its space and Earth shots (client/proxima/ProximaCosmos): a scene of its own, in its own units
// (the star's radius is 1, Earth's is 1), drawn over the world while those shots play.
#include <minecraft:projection.glsl>
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

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

layout(location = 0) out vec3 pos;
layout(location = 1) out vec2 uv;
layout(location = 2) out vec4 color;
layout(location = 3) out vec3 normal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    pos = Position;
    uv = UV0;
    color = Color;
    normal = Normal;
}
