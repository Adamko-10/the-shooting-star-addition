#version 150

// The shapes of SS-05 Halley's light, drawn additively on camera-relative quads (client/CometRenderer).
//
// uv carries the shape: u = shape * 4 + (x + 1) and v = variant * 4 + (y + 1), with x and y running -1..1 across the
// quad (for ribbons x runs from the head of the ribbon, -1, to its tail, +1). The vertex colour is the tint and its
// alpha the strength. Alpha written out is a small share of the light, so that with Fabulous graphics the
// transparency pass still blends it (it ignores fully transparent pixels).
uniform vec4 ColorModulator;
uniform float GameTime;
// 1 while painting the shapes onto a texture for shader packs (client/render/GlowAtlas)
uniform float Bake;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float GLOW = 0.0;
const float RING = 1.0;
const float GLINT = 2.0;
const float STREAK = 3.0;
const float BOW = 4.0;
const float CHEVRON = 5.0;
const float RETICLE = 6.0;
const float DISC = 7.0;
const float DASH = 8.0;
const float SHOCK = 9.0;
const float FLARE = 10.0;
const float WALL = 11.0;

float sq(float x) {
    return x * x;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += noise(p) * amp;
        p = p * 2.03 + vec2(17.1, 9.7);
        amp *= 0.5;
    }
    return sum;
}

// soft square edge, so no quad ever shows its border
float edge(vec2 p) {
    vec2 a = abs(p);
    return (1.0 - smoothstep(0.82, 1.0, a.x)) * (1.0 - smoothstep(0.82, 1.0, a.y));
}

void main() {
    float shape = floor(texCoord0.x / 4.0 + 0.0001);
    float variant = floor(texCoord0.y / 4.0 + 0.0001);
    vec2 p = vec2(texCoord0.x - shape * 4.0, texCoord0.y - variant * 4.0) - 1.0;
    float seconds = GameTime * 1200.0;
    float d = length(p);
    float light = 0.0;

    if (shape < GLOW + 0.5) {
        if (variant < 0.5) {
            // a star seen through air: white-hot core, a tight halo and a wide soft one
            float core = exp(-d * d * 60.0);
            float halo = exp(-d * 6.0) * 0.55 + exp(-d * 2.4) * 0.22;
            light = (core * 2.4 + halo) * (1.0 - smoothstep(0.7, 1.0, d));
        } else {
            // variant 1: only the haze, no hot core (light round something that glows by itself)
            light = (exp(-d * d * 5.0) * 0.8 + exp(-d * 2.6) * 0.25) * (1.0 - smoothstep(0.6, 1.0, d));
        }
    } else if (shape < RING + 0.5) {
        float width = 0.035 + 0.02 * variant;
        float ring = exp(-sq((d - 0.86) / width));
        float soft = 0.22 * exp(-sq((d - 0.82) / 0.16));
        light = (ring + soft) * (1.0 - smoothstep(0.96, 1.0, d));
    } else if (shape < GLINT + 0.5) {
        // four long spikes and four short ones, like a bright star through a lens
        vec2 a = abs(p);
        float spikes = exp(-a.y * 70.0) * exp(-a.x * 2.6) + exp(-a.x * 70.0) * exp(-a.y * 2.6);
        vec2 r = mat2(0.7071, 0.7071, -0.7071, 0.7071) * p;
        vec2 b = abs(r);
        float diagonal = exp(-b.y * 90.0) * exp(-b.x * 7.0) + exp(-b.x * 90.0) * exp(-b.y * 7.0);
        light = (spikes + diagonal * 0.4 + exp(-d * d * 120.0) * 2.0) * edge(p);
    } else if (shape < STREAK + 0.5) {
        // a tail: x = -1 at the head .. 1 at the end, y across. variant 0 = ion tail (straight, streaky, blue),
        // variant 1 = dust tail (broad, smooth), variant 2 = plasma wake (short, turbulent)
        float along = (p.x + 1.0) * 0.5;
        float across = p.y;
        float spread = variant > 0.5 ? 2.0 + 3.0 * along : 3.0 + 9.0 * along;
        float body = exp(-across * across * spread);
        // a rounded head: the near end swells in over the first tenth instead of starting on a hard edge
        float head = smoothstep(0.0, variant < 0.5 ? 0.05 : 0.12, along - 0.04 * across * across);
        float fade = pow(max(1.0 - along, 0.0), variant > 1.5 ? 2.0 : variant > 0.5 ? 1.1 : 0.85) * head;
        float flow = seconds * (variant > 1.5 ? 3.0 : 0.9);
        float streaks;
        if (variant < 0.5) {
            streaks = 0.55 + 0.75 * fbm(vec2(across * 9.0, along * 3.0 - flow));
        } else if (variant < 1.5) {
            streaks = 0.75 + 0.35 * fbm(vec2(across * 3.0, along * 2.0 - flow * 0.5));
        } else {
            streaks = 0.4 + 1.0 * fbm(vec2(across * 6.0 + flow, along * 6.0 - flow * 2.0));
        }
        light = body * fade * streaks * (1.0 - smoothstep(0.85, 1.0, abs(across)));
    } else if (shape < BOW + 0.5) {
        // the bow shock in front of the nucleus, facing +y
        float arc = exp(-sq((d - 0.55) / 0.1)) * smoothstep(-0.1, 0.55, p.y);
        // noise sampled round a circle, so it has no seam where the angle wraps
        vec2 round = p / max(d, 1.0e-4);
        float flicker = 0.85 + 0.3 * noise(round * 4.0 + vec2(seconds * 9.0, seconds * 5.3));
        light = arc * flicker * (1.0 - smoothstep(0.85, 1.0, d)) * 1.4;
    } else if (shape < CHEVRON + 0.5) {
        // an arrowhead pointing +y, for the approach corridor projected on the land
        float line = abs(p.y + abs(p.x) * 0.85 - 0.15);
        light = exp(-sq(line / 0.11)) * (1.0 - smoothstep(0.75, 0.95, abs(p.x))) * edge(p);
    } else if (shape < RETICLE + 0.5) {
        // the mark: an outer ring with four ticks, an inner dashed ring turning, a dot
        float outer = exp(-sq((d - 0.86) / 0.03));
        float angle = atan(p.y, p.x);
        // four thin ticks on the axes, between the rings
        float ticks = exp(-sq(min(abs(p.x), abs(p.y)) / 0.016)) * smoothstep(0.6, 0.64, d) * (1.0 - smoothstep(0.78, 0.82, d));
        float dashes = exp(-sq((d - 0.55) / 0.025)) * step(0.0, sin(angle * 12.0 + seconds * 1.6));
        float dot = exp(-d * d * 220.0) * 1.5;
        light = (outer + ticks + dashes * 0.8 + dot) * (1.0 - smoothstep(0.95, 1.0, d));
    } else if (shape < DISC + 0.5) {
        light = (1.0 - smoothstep(0.55, 1.0, d)) * (0.55 + 0.45 * (1.0 - d * d));
    } else if (shape < DASH + 0.5) {
        vec2 a = abs(p);
        light = (1.0 - smoothstep(0.6, 1.0, a.x)) * exp(-a.y * a.y * 6.0);
    } else if (shape > FLARE - 0.5 && shape < FLARE + 0.5) {
        // a lens flare: a thin line across the screen with a soft glow along it and a hot core
        vec2 a = abs(p);
        float line = exp(-a.y * a.y * 40.0) * pow(1.0 - a.x, 1.6);
        float soft = exp(-a.y * a.y * 5.0) * pow(1.0 - a.x, 3.0) * 0.3;
        float core = exp(-(a.x * a.x * 70.0 + a.y * a.y * 6.0)) * 0.9;
        light = (line + soft + core) * (1.0 - smoothstep(0.85, 1.0, a.y));
    } else if (shape > WALL - 0.5 && shape < WALL + 0.5) {
        // a wall of snow thrown up by the shock front: x along it, y = -1 at the ground .. 1 at the top
        float h = (p.y + 1.0) * 0.5;
        float top = 0.5 + 0.4 * fbm(vec2(p.x * 2.5 + variant * 3.7, seconds * 0.9));
        float body = (1.0 - smoothstep(top - 0.3, top, h)) * (0.5 + 0.7 * fbm(vec2(p.x * 6.0 + variant * 3.7, h * 5.0 - seconds * 2.0)));
        float base = exp(-h * 7.0) * 0.7;
        light = (body * (1.0 - 0.5 * h) + base) * (1.0 - smoothstep(0.97, 1.0, abs(p.x)));
    } else {
        // a shock front: a bright, ragged ring
        // ragged by noise sampled round a circle (no seam where the angle wraps)
        vec2 round = p / max(d, 1.0e-4);
        float rag = 0.05 * (fbm(round * 2.2 + vec2(variant * 7.0 + seconds * 0.7, seconds * 0.4)) - 0.5);
        float ring = exp(-sq((d - 0.9 - rag) / 0.035));
        float wash = 0.35 * smoothstep(0.35, 0.9, d) * (1.0 - smoothstep(0.88, 0.96, d));
        light = (ring + wash) * (1.0 - smoothstep(0.97, 1.0, d));
    }

    if (Bake > 0.5) {
        // the light alone, softly clipped into 0..1 (a texture can't hold more), and where there is any
        fragColor = vec4(vec3(1.0 - exp(-light)), clamp(light * 50.0, 0.0, 1.0));
        return;
    }

    float strength = vertexColor.a * light;
    vec3 colour = vertexColor.rgb * strength * ColorModulator.rgb;
    // whiten the hottest parts, the way an over-exposed light reads
    colour += vec3(max(strength - 1.0, 0.0) * 0.35);
    fragColor = vec4(colour, clamp(strength * 0.12, 0.0, 1.0) * ColorModulator.a);
}
