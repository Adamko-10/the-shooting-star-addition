#version 150

// SS-05 Halley's sky, drawn over the vanilla one while a strike runs (client/sky/HalleySky).
//
// As the comet comes in, the sky goes to night: stars, the band of the galaxy, an aurora, the comet's light thrown
// across the air. After the impact the air is full of ice: a pale haze with a 22° halo, sun dogs and a light pillar
// round the sun (or the moon), clearing as the crystals settle.
//
// Output is premultiplied: rgb = the new sky times how much of the old one it covers, plus light added on top;
// alpha = that coverage. Blend: ONE, ONE_MINUS_SRC_ALPHA. Everything is a function of the view direction (world axes,
// from the camera), so the sky stays put as the camera turns.
uniform float SkyClock;
uniform float Night;
uniform float Stars;
uniform float Aurora;
uniform float Veil;
uniform float Halo;
uniform float Flash;
uniform vec3 CometDir;
uniform float CometGlow;
uniform float CometHeat;
uniform vec3 TailDir;
uniform vec3 LightDir;
uniform float LightUp;
uniform float Seed;
// what to draw: 0 = the dome as above; for shader packs, painted onto textures (HalleySky.paint): 1 = only the new
// sky's colour (premultiplied; how much it covers is drawn separately), 2 = only the light on top
uniform float Layer;

in vec3 direction;

out vec4 fragColor;

// ---- The colours of the dark sky (HalleySky.HORIZON must match the horizon one, for the fog). ----------------------
const vec3 ZENITH = vec3(0.010, 0.017, 0.055);
const vec3 MIDDLE = vec3(0.022, 0.045, 0.125);
const vec3 HORIZON = vec3(0.055, 0.110, 0.215);
const float HALO_RADIUS = 0.3840;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = hash33(i).x;
    float b = hash33(i + vec3(1.0, 0.0, 0.0)).x;
    float c = hash33(i + vec3(0.0, 1.0, 0.0)).x;
    float d = hash33(i + vec3(1.0, 1.0, 0.0)).x;
    float e = hash33(i + vec3(0.0, 0.0, 1.0)).x;
    float f1 = hash33(i + vec3(1.0, 0.0, 1.0)).x;
    float g = hash33(i + vec3(0.0, 1.0, 1.0)).x;
    float h = hash33(i + vec3(1.0, 1.0, 1.0)).x;
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, f1, u.x), mix(g, h, u.x), u.y), u.z);
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 3; i++) {
        sum += noise3(p) * amp;
        p = p * 2.07 + vec3(11.3, 5.1, 7.7);
        amp *= 0.5;
    }
    return sum;
}

// ---- The night sky. ------------------------------------------------------------------------------------------------

vec3 nightSky(vec3 d) {
    float h = d.y;
    vec3 c = mix(HORIZON, MIDDLE, smoothstep(0.0, 0.3, h));
    c = mix(c, ZENITH, smoothstep(0.3, 1.0, h));
    return h < 0.0 ? mix(HORIZON, HORIZON * 0.55, smoothstep(0.0, -0.35, h)) : c;
}

// the face of the cube a direction goes through, and where on it: a flat grid to scatter stars on
vec2 faceUv(vec3 d, out float face) {
    vec3 a = abs(d);
    if (a.x >= a.y && a.x >= a.z) {
        face = d.x > 0.0 ? 0.0 : 1.0;
        return d.yz / a.x;
    }
    if (a.y >= a.z) {
        face = d.y > 0.0 ? 2.0 : 3.0;
        return d.xz / a.y;
    }
    face = d.z > 0.0 ? 4.0 : 5.0;
    return d.xy / a.z;
}

vec3 stars(vec3 d) {
    float face;
    vec2 uv = faceUv(d, face);
    // how big a pixel is on the grid: stars never get thinner than that, so they don't flicker as the camera moves
    vec2 px2 = fwidth(uv);
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 2; layer++) {
        float scale = layer == 0 ? 70.0 : 190.0;
        float keep = layer == 0 ? 0.42 : 0.22;
        vec2 g = uv * scale;
        vec2 cell = floor(g);
        float px = clamp(max(px2.x, px2.y) * scale, 0.02, 0.6);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec2 c = cell + vec2(float(x), float(y));
                vec3 h = hash33(vec3(c, face * 13.0 + float(layer) * 71.0 + Seed));
                if (h.z > keep) {
                    continue;
                }
                vec2 at = c + 0.15 + 0.7 * h.xy;
                float dist = length(g - at);
                float mag = pow(hash12(c * 1.37 + vec2(face, float(layer) * 3.0) + Seed), 5.0);
                float size = 0.045 + 0.1 * mag;
                float sigma = max(size, px * 0.7);
                float energy = size * size / (sigma * sigma);
                float b = energy * exp(-dist * dist / (2.0 * sigma * sigma)) * (0.25 + 3.2 * mag);
                float twinkle = 0.7 + 0.3 * sin(SkyClock * (1.3 + 4.5 * h.y) + h.x * 41.0);
                vec3 tint = mix(vec3(0.62, 0.76, 1.0), vec3(1.0, 0.86, 0.72), hash12(c * 3.1 + 7.0 + face));
                col += tint * b * twinkle * (layer == 0 ? 1.0 : 0.6);
            }
        }
    }
    return col * smoothstep(-0.02, 0.25, d.y);
}

// the band of the galaxy across the sky, with dark lanes of dust in it
vec3 galaxy(vec3 d) {
    vec3 pole = normalize(vec3(0.42, 0.55, -0.72));
    float b = dot(d, pole);
    float band = exp(-b * b / 0.03) + 0.35 * exp(-b * b / 0.15);
    if (band < 0.01) {
        return vec3(0.0);
    }
    float cloud = fbm3(d * 3.2 + vec3(Seed * 0.01));
    float lanes = smoothstep(0.42, 0.62, fbm3(d * 8.5 + vec3(3.1, Seed * 0.02, 1.7)));
    vec3 c = mix(vec3(0.20, 0.17, 0.42), vec3(0.17, 0.36, 0.56), cloud);
    c = mix(c, vec3(0.45, 0.40, 0.55), smoothstep(0.65, 0.85, cloud) * 0.5);
    return c * band * (0.2 + 0.8 * cloud * cloud) * (0.45 + 0.55 * lanes) * 0.42 * smoothstep(-0.02, 0.25, d.y);
}

// one fold of an aurora's curtain, seen from below at plane position p
float curtain(vec2 p, float t) {
    vec2 w = p + 0.9 * vec2(noise(p * 0.45 + vec2(t, 1.3)), noise(p * 0.45 + vec2(4.7, -t)));
    float v = noise(vec2(w.x * 0.8, w.y * 0.22) + vec2(0.0, t * 0.6));
    float ridge = 1.0 - abs(v * 2.0 - 1.0);
    return pow(ridge, 12.0) * (0.6 + 0.4 * noise(w * 3.0 + t * 2.0));
}

vec3 aurora(vec3 d) {
    if (d.y < 0.01) {
        return vec3(0.0);
    }
    float t = SkyClock * 0.035;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 10; i++) {
        float layer = float(i) / 10.0;
        // where this height of the curtains crosses the line of sight
        vec2 p = d.xz / (d.y + 0.035) * (1.0 + layer * 0.55) * 0.55 + vec2(Seed * 0.17, Seed * 0.11);
        float c = curtain(p, t);
        vec3 col = mix(vec3(0.10, 1.0, 0.62), vec3(0.30, 0.70, 1.0), smoothstep(0.0, 0.5, layer));
        col = mix(col, vec3(0.72, 0.36, 1.0), smoothstep(0.45, 1.0, layer));
        acc += col * c * (1.0 - layer * 0.8) * 0.2;
    }
    return acc * smoothstep(0.02, 0.16, d.y) * (1.0 - smoothstep(0.55, 0.95, d.y));
}

// the comet's light thrown across the air round it, and low along the horizon under it
vec3 cometLight(vec3 d) {
    float c = max(dot(d, CometDir), 0.0);
    float near = pow(c, 1400.0) * 1.1 + pow(c, 160.0) * 0.45 + pow(c, 22.0) * 0.16 + pow(c, 4.0) * 0.05;
    vec2 dh = d.xz / max(length(d.xz), 1.0e-4);
    vec2 ch = CometDir.xz / max(length(CometDir.xz), 1.0e-4);
    float under = exp(-abs(d.y) * 6.0) * pow(max(dot(dh, ch), 0.0), 5.0) * 0.22;
    // a faint broad glow along the tails as they stream away
    float along = max(dot(d, TailDir), 0.0);
    float tail = pow(c, 6.0) * pow(along, 2.0) * 0.08;
    vec3 col = mix(vec3(0.32, 0.72, 1.0), vec3(1.0, 0.70, 0.40), CometHeat);
    return col * (near + under + tail) * CometGlow;
}

// ---- The ice in the air after the impact. ---------------------------------------------------------------------------

vec3 veilSky(vec3 d) {
    float h = d.y;
    vec3 c = mix(vec3(0.56, 0.76, 0.90), vec3(0.30, 0.50, 0.76), smoothstep(0.0, 0.85, h));
    return c + vec3(0.07, 0.08, 0.08) * exp(-max(h, 0.0) * 9.0);
}

vec3 halo(vec3 d) {
    float a = acos(clamp(dot(d, LightDir), -1.0, 1.0));
    float ring = exp(-pow((a - HALO_RADIUS) / 0.008, 2.0));
    float red = exp(-pow((a - HALO_RADIUS + 0.01) / 0.005, 2.0));
    float outside = step(HALO_RADIUS, a) * exp(-(a - HALO_RADIUS) / 0.05) * 0.16;
    // inside the halo the sky is a little darker, as it is under a real one
    float inside = (1.0 - smoothstep(HALO_RADIUS - 0.08, HALO_RADIUS - 0.01, a)) * smoothstep(0.03, 0.12, a);
    vec3 col = vec3(0.95, 0.97, 1.0) * ring * 0.5 + vec3(1.0, 0.5, 0.3) * red * 0.3 + vec3(0.8, 0.9, 1.0) * outside
        - vec3(0.06, 0.05, 0.03) * inside;

    float el = asin(clamp(LightDir.y, -1.0, 1.0));
    float pe = asin(clamp(d.y, -1.0, 1.0));
    vec2 lh = LightDir.xz / max(length(LightDir.xz), 1.0e-4);
    vec2 dh = d.xz / max(length(d.xz), 1.0e-4);
    float daz = acos(clamp(dot(lh, dh), -1.0, 1.0));
    // sun dogs: bright spots level with the light, just outside the halo (further out as it climbs), red toward it
    float dogAz = HALO_RADIUS / max(cos(el), 0.35);
    float dog = exp(-pow((pe - el) / 0.02, 2.0)) * exp(-pow((daz - dogAz) / 0.024, 2.0));
    float dogTail = exp(-pow((pe - el) / 0.008, 2.0)) * step(dogAz, daz) * exp(-(daz - dogAz) / 0.14) * 0.3;
    vec3 dogCol = mix(vec3(1.0, 0.45, 0.28), vec3(0.62, 0.82, 1.0), smoothstep(dogAz - 0.02, dogAz + 0.035, daz));
    col += dogCol * dog * 1.7 + vec3(0.9, 0.95, 1.0) * dogTail * 0.8;
    // the parhelic circle: a thin white ring level with the light, all the way round
    col += vec3(0.85, 0.92, 1.0) * exp(-pow((pe - el) / 0.005, 2.0)) * 0.07;
    // the light pillar, straight up and down through it
    col += vec3(1.0, 0.95, 0.86) * exp(-pow(daz * cos(el) / 0.007, 2.0)) * exp(-abs(pe - el) / 0.2) * 0.45;
    // the light itself, softened by the haze
    col += vec3(1.0, 0.98, 0.92) * (exp(-a * a / 0.00025) * 2.5 + exp(-a / 0.06) * 0.45);
    return col;
}

void main() {
    vec3 d = normalize(direction);

    // the new sky over the old: first the night, then the haze over that (premultiplied)
    vec3 sky = nightSky(d) * Night * (1.0 - Veil * 0.75) + veilSky(d) * Veil * 0.75;
    float cover = 1.0 - (1.0 - Night) * (1.0 - Veil * 0.75);
    if (Layer > 0.5 && Layer < 1.5) {
        fragColor = vec4(sky, 1.0);
        return;
    }

    vec3 light = vec3(0.0);
    if (Stars > 0.001) {
        light += (stars(d) + galaxy(d)) * Stars;
    }
    if (Aurora > 0.001) {
        light += aurora(d) * Aurora;
    }
    if (CometGlow > 0.001) {
        light += cometLight(d);
    }
    if (Halo > 0.001) {
        light += halo(d) * Halo * LightUp;
    }
    light += vec3(0.85, 0.95, 1.0) * Flash;
    if (Layer > 1.5) {
        fragColor = vec4(max(light, 0.0), 1.0);
        return;
    }

    // a touch of noise, so the dark gradient doesn't band
    vec3 dither = vec3((hash12(gl_FragCoord.xy) - 0.5) / 255.0);
    fragColor = vec4(max(sky + light + dither * cover, 0.0), cover);
}
