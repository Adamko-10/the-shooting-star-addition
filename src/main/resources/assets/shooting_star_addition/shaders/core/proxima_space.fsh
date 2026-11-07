#version 150

// SA-04 Proxima's film, its space and Earth shots (client/proxima/ProximaCosmos). One program, a material per Mode:
//  0 the deep-space backdrop (Param.x: the red nebula, Param.y: how many stars), a cube round the camera
//  1 Proxima Centauri's surface: boiling granules, dark starspots, darker toward its limb (Param.x: how bright)
//  2 the Dyson sphere's panels (uv: the corner weights, for the glowing seams; Color.r: each panel's own number)
//  3 a soft round glow (uv across it; Color: its colour, Color.a: how strong; Param.x: a gain past 1), added
//  4 a band of light (uv.x across it, uv.y along it; Param.x: gain, Param.y: flowing noise along it, Param.z: dashes
//    along it), added
//  5 Earth (Sampler0: the day side, Sampler1: the city lights; SunDir), 6 its clouds (Sampler0), 7 its air, added
// (the spheres, the star's and Earth's, sit on the scene's origin: their normal is their position)
//  8 the gate's disc of plasma (uv: -1..1 across it; Param.x: how strong), added
//  9 the coronal mass ejection streaming from the gate (uv.x round it, uv.y along it; Param.x: strength, Param.y: how
//    far it has run), added
// 10 plain colour (Color), 11 a lens tile (uv across it; Param.x: its charge)
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Mode;
uniform float Time;
uniform vec3 CamPos;
uniform vec3 SunDir;
uniform vec4 Param;

in vec3 pos;
in vec2 uv;
in vec4 color;
in vec3 normal;

out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i), hash31(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash31(i + vec3(0.0, 1.0, 0.0)), hash31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash31(i + vec3(0.0, 0.0, 1.0)), hash31(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash31(i + vec3(0.0, 1.0, 1.0)), hash31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// a filmic roll-off, so bright parts glow to white instead of clipping flat
vec3 tone(vec3 c) {
    return vec3(1.0) - exp(-c * 1.15);
}

// one layer of stars on the sky: a star in some cells of a grid round the camera
vec3 stars(vec3 d, float scale, float density, float size) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    if (hash31(cell) > density) {
        return vec3(0.0);
    }
    vec3 at = cell + vec3(hash31(cell + 17.1), hash31(cell + 31.7), hash31(cell + 53.3)) * 0.6 + 0.2;
    float b = hash31(cell + 7.7);
    float glow = smoothstep(size, 0.0, length(p - at)) * (0.3 + 1.6 * b * b * b);
    vec3 tint = b > 0.85 ? vec3(0.7, 0.8, 1.0) : (b < 0.15 ? vec3(1.0, 0.75, 0.6) : vec3(1.0));
    return tint * glow;
}

void main() {
    int mode = int(Mode + 0.5);
    vec3 view = normalize(CamPos - pos);
    if (mode == 0) {
        vec3 d = normalize(pos - CamPos);
        float a = pow(fbm(d * 2.6), 3.0);
        float b = pow(fbm(d * 1.3 + 5.0), 2.0);
        vec3 c = (vec3(0.10, 0.018, 0.04) * a * 1.4 + vec3(0.03, 0.02, 0.07) * b * 1.1) * Param.x;
        c += stars(d, 340.0, 0.09 * Param.y, 0.32);
        c += stars(d, 120.0, 0.03 * Param.y, 0.22) * 1.3;
        fragColor = vec4(c, 1.0);
    } else if (mode == 1) {
        float g1 = fbm(pos * 3.5 + vec3(0.0, Time * 0.07, Time * 0.05));
        float g2 = fbm(pos * 13.0 + vec3(Time * 0.22, 0.0, -Time * 0.18));
        float cells = smoothstep(0.38, 0.78, g2);
        float spots = smoothstep(0.63, 0.71, fbm(pos * 1.7 + 11.0));
        vec3 c = mix(vec3(0.45, 0.03, 0.015), vec3(1.0, 0.24, 0.05), smoothstep(0.25, 0.75, g1));
        c = mix(c, vec3(1.0, 0.72, 0.42), cells * 0.6 * g1);
        c *= 1.0 - 0.8 * spots;
        float mu = clamp(dot(normalize(pos), view), 0.0, 1.0);
        c *= 0.3 + 0.7 * pow(mu, 0.45);
        fragColor = vec4(tone(c * Param.x), 1.0);
    } else if (mode == 2) {
        float edge = min(min(uv.x, uv.y), 1.0 - uv.x - uv.y);
        float seam = 1.0 - smoothstep(0.0, 0.028, edge);
        float seed = color.r;
        vec3 n = normalize(normal);
        float facing = dot(n, view);
        vec3 c;
        if (facing > 0.0) {
            // the outside: dark plating, a rim of starlight round the shell's outline, a few lit hatches
            float fres = pow(1.0 - abs(facing), 2.5);
            c = vec3(0.03, 0.028, 0.034) * (0.6 + 0.9 * seed) + vec3(0.9, 0.22, 0.12) * fres * 0.12;
            float hatch = step(0.965, seed) * step(0.3, fract(Time * 0.3 + seed * 7.0));
            c += vec3(1.4, 0.55, 0.25) * hatch * (1.0 - smoothstep(0.08, 0.25, edge));
        } else {
            // the inside, lit by the star
            c = vec3(0.9, 0.18, 0.06) * (0.45 + 0.4 * seed);
        }
        c += vec3(1.2, 0.32, 0.12) * seam * (0.35 + 0.2 * sin(Time * 1.7 + seed * 30.0));
        fragColor = vec4(tone(c), 1.0);
    } else if (mode == 3) {
        float r = length(uv * 2.0 - 1.0);
        float g = exp(-r * r * 14.0) * 0.55 + 0.45 * pow(max(0.0, 1.0 - r), 3.0);
        fragColor = vec4(color.rgb * color.a * g * Param.x, 1.0);
    } else if (mode == 4) {
        float across = 1.0 - abs(uv.x * 2.0 - 1.0);
        float k = pow(across, 1.6);
        if (Param.y > 0.0) {
            k *= 0.7 + 0.3 * vnoise(vec3(uv.y * Param.y - Time * 30.0, 0.0, 0.0));
        }
        if (Param.z > 0.0) {
            k *= step(0.82, fract(uv.y * Param.z - Time * 0.6));
        }
        fragColor = vec4(color.rgb * color.a * k * Param.x, 1.0);
    } else if (mode == 5) {
        vec3 n = normalize(pos);
        float ndl = dot(n, SunDir);
        float day = smoothstep(-0.12, 0.25, ndl);
        vec3 d = texture(Sampler0, uv).rgb;
        float lights = texture(Sampler1, uv).r;
        vec3 c = d * (0.025 + 1.15 * day);
        c += vec3(1.0, 0.72, 0.38) * lights * 1.8 * (1.0 - smoothstep(-0.25, 0.05, ndl));
        float ocean = smoothstep(0.02, -0.04, d.g - d.b) * smoothstep(0.35, 0.1, d.r);
        vec3 h = normalize(SunDir + view);
        c += vec3(1.0, 0.92, 0.8) * pow(max(dot(n, h), 0.0), 140.0) * ocean * day * 0.45;
        float rim = pow(1.0 - max(dot(n, view), 0.0), 3.0);
        c = mix(c, vec3(0.35, 0.6, 1.0), rim * 0.55 * smoothstep(-0.3, 0.3, ndl));
        fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
    } else if (mode == 6) {
        float a = texture(Sampler0, uv).a;
        float day = smoothstep(-0.12, 0.25, dot(normalize(pos), SunDir));
        fragColor = vec4(vec3(0.04 + 0.96 * day), a * (0.2 + 0.8 * day));
    } else if (mode == 7) {
        vec3 n = normalize(pos);
        float z = dot(n, view);
        if (z > 0.0) {
            discard;
        }
        float i = pow(clamp(0.72 + z, 0.0, 1.0), 4.0);
        float lit = smoothstep(-0.4, 0.4, dot(n, SunDir));
        fragColor = vec4(vec3(0.22, 0.45, 1.0) * i * 0.75 * (0.12 + 0.88 * lit), 1.0);
    } else if (mode == 8) {
        vec2 p = uv;
        float r = length(p);
        float a = atan(p.y, p.x);
        float swirl = fbm(vec3(cos(a + r * 4.0 - Time * 2.0) * 2.0, sin(a + r * 4.0 - Time * 2.0) * 2.0, r * 3.0 - Time));
        float edge = smoothstep(1.0, 0.85, r);
        vec3 c = mix(vec3(0.9, 0.12, 0.25), vec3(1.3, 1.0, 1.15), pow(swirl, 3.0) * 2.0) * swirl * edge * (0.25 + 0.75 * r);
        fragColor = vec4(c * Param.x, 1.0);
    } else if (mode == 9) {
        float along = uv.y;
        if (along > Param.y) {
            discard;
        }
        float streak = pow(fbm(vec3(uv.x * 26.0, along * 5.0 - Time * 3.0, 0.0)), 2.2);
        float rim = pow(1.0 - abs(dot(normalize(normal), view)), 1.5);
        float head = smoothstep(Param.y - 0.08, Param.y, along);
        float fade = smoothstep(0.0, 0.08, along) * (1.0 - smoothstep(0.85, 1.0, along));
        vec3 c = mix(vec3(0.8, 0.07, 0.22), vec3(1.4, 0.9, 1.0), head) * (0.15 + 1.6 * streak) * (0.35 + rim) * fade * Param.x;
        fragColor = vec4(c, 1.0);
    } else if (mode == 11) {
        float r = length(uv * 2.0 - 1.0);
        float rim = smoothstep(0.62, 0.95, r);
        vec3 c = vec3(0.06, 0.05, 0.06) + vec3(1.6, 0.5, 0.25) * rim * 0.9 + vec3(2.2, 1.6, 1.9) * Param.x * (0.4 + 0.6 * rim);
        fragColor = vec4(tone(c), 1.0);
    } else {
        fragColor = color;
    }
}
