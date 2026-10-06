#version 150

// SS-05 Halley's sky (client/sky/HalleySky): a cube round the camera, so every pixel knows which way it looks.
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;

out vec3 direction;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    direction = Position;
}
