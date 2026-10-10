# Generates SS-05 Halley's pixel art: the two block textures (animated, 12 frames like the remote's own blocks) and
# the 64x32 skill card for the Stellar Remote's menu.
import numpy as np
from PIL import Image
import os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
rng = np.random.default_rng(5)

def hexrgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)

def tile_noise(n, cells, seed):
    """Seamless value noise on an n x n tile."""
    r = np.random.default_rng(seed)
    g = r.random((cells, cells))
    xs = np.arange(n) * cells / n
    i0 = np.floor(xs).astype(int) % cells
    i1 = (i0 + 1) % cells
    f = xs - np.floor(xs)
    f = f * f * (3 - 2 * f)
    a = g[i0][:, i0] * (1 - f)[None, :] + g[i0][:, i1] * f[None, :]
    b = g[i1][:, i0] * (1 - f)[None, :] + g[i1][:, i1] * f[None, :]
    return a * (1 - f)[:, None] + b * f[:, None]

def palette(values, stops):
    """Map 0..1 values to colours through stops [(v, 0xRRGGBB)], banded like pixel art (no smooth blend)."""
    out = np.zeros(values.shape + (3,))
    for i, (v, c) in enumerate(stops):
        mask = values >= v
        out[mask] = hexrgb(c)
    return out

def save_anim(path, frames):
    sheet = np.concatenate(frames, axis=0)
    Image.fromarray(np.clip(sheet, 0, 255).astype(np.uint8), 'RGB').save(path)

N = 16
FRAMES = 12
yy, xx = np.mgrid[0:N, 0:N]

# ---- Comet heart: faceted blue ice, a sheen sweeping across it, sparks of light ------------------------------------
facets = (np.floor((xx + yy) / 4) + np.floor((xx - yy + 16) / 4)) % 3          # diagonal facet pattern
facet_noise = tile_noise(N, 4, 11)
base = 0.35 + 0.18 * facets + 0.25 * facet_noise
heart_frames = []
sparks = [(rng.integers(0, 16), rng.integers(0, 16), rng.integers(0, FRAMES)) for _ in range(5)]
for f in range(FRAMES):
    phase = f / FRAMES
    # the sheen: a diagonal band moving across, wrapping round so the loop is seamless
    d = ((xx + yy) / 32.0 - phase) % 1.0
    sheen = np.exp(-((d - 0.5) / 0.08) ** 2) * 0.45
    pulse = 0.06 * np.sin(phase * 2 * np.pi)
    v = np.clip(base + sheen + pulse, 0, 1)
    img = palette(v, [(0.0, 0x1E5FA8), (0.32, 0x2F86D6), (0.45, 0x4FB4F0), (0.58, 0x7FD9FF), (0.72, 0xB8F0FF), (0.86, 0xEEFFFF)])
    for (sx, sy, sf) in sparks:
        k = (f - sf) % FRAMES
        if k == 0:
            img[sy, sx] = hexrgb(0xFFFFFF)
        elif k == 1:
            img[sy, sx] = hexrgb(0xD8FBFF)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[(sy + dy) % N, (sx + dx) % N] = np.maximum(img[(sy + dy) % N, (sx + dx) % N], hexrgb(0xA8EEFF))
    heart_frames.append(img)
os.makedirs(OUT + '/assets/shooting_star_addition/textures/block', exist_ok=True)
save_anim(OUT + '/assets/shooting_star_addition/textures/block/comet_heart.png', heart_frames)

# ---- Frozen comet trail: pale scored ice, streaks of frost flowing along it -----------------------------------------
streak_noise = tile_noise(N, 8, 21)
mottle = tile_noise(N, 4, 22)
trail_frames = []
glints = [(rng.integers(0, 16), rng.integers(0, 16), rng.integers(0, FRAMES)) for _ in range(4)]
for f in range(FRAMES):
    phase = f / FRAMES
    # long streaks along x (scored by the nucleus), their brightness flowing along them
    rows = np.sin((yy / 16.0) * 2 * np.pi * 3 + streak_noise * 2.0) * 0.5 + 0.5
    flow = 0.5 + 0.5 * np.sin(2 * np.pi * ((xx / 16.0) - phase) + yy * 0.7)
    v = 0.38 + 0.22 * mottle + 0.22 * rows * flow
    img = palette(np.clip(v, 0, 1), [(0.0, 0x3A78B8), (0.42, 0x5AA0DA), (0.52, 0x86C6EE), (0.62, 0xB4E4FA), (0.74, 0xDFF7FF)])
    for (sx, sy, sf) in glints:
        if (f - sf) % FRAMES == 0:
            img[sy, sx] = hexrgb(0xFFFFFF)
    trail_frames.append(img)
save_anim(OUT + '/assets/shooting_star_addition/textures/block/comet_trail.png', trail_frames)

# ---- The skill card: 64x32, the remote's menu style (dithered night sky, Earth's limb, the strike) ----------------
W, H = 64, 32
card = np.zeros((H, W, 3))
Y, X = np.mgrid[0:H, 0:W]
sky_top, sky_bot = hexrgb(0x070B24), hexrgb(0x1B2560)
k = (Y / (H - 1))[..., None]
card[:] = sky_top * (1 - k) + sky_bot * k
# checker dither between two shades, as the other cards do
dither = ((X + Y) % 2 == 0)[..., None]
card = np.where(dither, card * 1.08, card * 0.94)
# stars
for _ in range(26):
    sx, sy = rng.integers(0, W), rng.integers(0, 22)
    card[sy, sx] = hexrgb(0xFFFFFF) if rng.random() < 0.35 else hexrgb(0x9FB4E8)
# Earth's limb, lower right: a big circle seen edge-on, ocean blue with a pale atmosphere line
cx, cy, r = 50.0, 118.0, 92.0
dist = np.sqrt((X - cx) ** 2 + ((Y - cy) * 1.0) ** 2)
earth = dist < r
card[earth] = hexrgb(0x1F5FB0)
card[earth & (dist > r - 3)] = hexrgb(0x2E86D8)
card[earth & ((X + Y) % 2 == 0) & (dist < r - 6)] = hexrgb(0x184E98)
atmo = (dist >= r) & (dist < r + 1.2)
card[atmo] = card[atmo] * 0.4 + hexrgb(0x8FD8FF) * 0.6
# the comet: its tails streaming back to the upper left, the head coming down toward the limb
hx, hy = 44.0, 19.0
def streak(card, direction, length, width0, width1, colour, strength, bend=0.0, cut=0.18):
    d = np.array(direction, dtype=float); d /= np.linalg.norm(d)
    rx, ry = X - hx, Y - hy
    along = rx * d[0] + ry * d[1]
    across = rx * -d[1] + ry * d[0]
    a = np.clip(along, 0, None)
    off = across - bend * a * a
    width = width0 + (width1 - width0) * np.clip(a / length, 0, 1)
    body = np.exp(-(off / width) ** 2) * np.clip(1 - a / length, 0, 1) ** 0.9 * (along > -0.6)
    body = np.where(body < cut, 0, np.minimum(1, body * 1.25))     # hard pixel edges
    return card * (1 - body[..., None] * strength) + hexrgb(colour) * body[..., None] * strength
tail = (-38.0, -17.0)
card = streak(card, (-37.0, -21.0), 32.0, 1.2, 4.2, 0x9FC8F0, 0.42, bend=0.004)   # dust tail, broad, curving away
card = streak(card, tail, 44.0, 1.4, 2.6, 0x2E7FD8, 0.8)                           # ion tail, blue glow
card = streak(card, tail, 34.0, 0.7, 1.1, 0x6FD8FF, 0.95)                          # its bright cyan core
card = streak(card, tail, 14.0, 0.55, 0.6, 0xE8FDFF, 1.0, cut=0.3)                 # white-hot near the head
# head: a white core in a cyan coma, with a four-pointed glint
core = np.sqrt((X - hx) ** 2 + (Y - hy) ** 2)
card[core < 3.2] = card[core < 3.2] * 0.25 + hexrgb(0x6FE8FF) * 0.75
card[core < 1.9] = hexrgb(0xDFFBFF)
card[int(hy), int(hx)] = hexrgb(0xFFFFFF)
for d in range(2, 6):
    fade = 1.0 - (d - 2) / 4.0
    for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
        yy2, xx2 = int(hy) + dy, int(hx) + dx
        if 0 <= yy2 < H and 0 <= xx2 < W:
            card[yy2, xx2] = card[yy2, xx2] * (1 - 0.8 * fade) + hexrgb(0xC8F6FF) * 0.8 * fade
# where it will land: a cold glow on the limb ahead of it, and the line it will plough
lx, ly = 56, 24
for y in range(H):
    for x in range(W):
        g = np.exp(-(((x - lx) / 4.0) ** 2 + ((y - ly) / 1.6) ** 2))
        if g > 0.15 and earth[y, x] or (g > 0.4):
            card[y, x] = card[y, x] * (1 - 0.6 * g) + hexrgb(0x9FEFFF) * 0.6 * g
for dx in range(-6, 5):
    x = lx + dx
    y = ly + (0 if dx < -2 else 1 if dx < 2 else 2)
    if 0 <= x < W and 0 <= y < H:
        card[y, x] = card[y, x] * 0.35 + hexrgb(0xCFF8FF) * 0.65
os.makedirs(OUT + '/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote', exist_ok=True)
Image.fromarray(np.clip(card, 0, 255).astype(np.uint8), 'RGB').save(
    OUT + '/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/halley.png')

# =====================================================================================================================
# SS-06 Luna's pixel art: moon cheese's two block textures (the molten one animated), the skill card, the mob effect
# icon, and the sky moon's two equirectangular textures. A separate rng (seeded differently) so none of this touches
# the draws above - re-running this file leaves every halley_*.png byte-identical.
# =====================================================================================================================
rng2 = np.random.default_rng(606)

def tile_noise_rect(w, h, cells_x, cells_y, seed, wrap_y=True):
    """Like tile_noise, but a w x h rectangle with its own cell counts; wraps in x always, in y only if asked."""
    r = np.random.default_rng(seed)
    g = r.random((cells_y, cells_x))
    xs = np.arange(w) * cells_x / w
    ix0 = np.floor(xs).astype(int) % cells_x
    ix1 = (ix0 + 1) % cells_x
    fx = xs - np.floor(xs); fx = fx * fx * (3 - 2 * fx)
    ys = np.arange(h) * cells_y / h
    iy0 = np.floor(ys).astype(int) % cells_y
    iy1 = ((iy0 + 1) % cells_y) if wrap_y else np.minimum(iy0 + 1, cells_y - 1)
    fy = ys - np.floor(ys); fy = fy * fy * (3 - 2 * fy)
    a = g[iy0][:, ix0] * (1 - fx)[None, :] + g[iy0][:, ix1] * fx[None, :]
    b = g[iy1][:, ix0] * (1 - fx)[None, :] + g[iy1][:, ix1] * fx[None, :]
    return a * (1 - fy)[:, None] + b * fy[:, None]

def wrapped_dist(xx_, yy_, cx, cy, w, wrap_y=False, h=None):
    dx = np.abs(xx_ - cx); dx = np.minimum(dx, w - dx)
    dy = np.abs(yy_ - cy)
    if wrap_y:
        dy = np.minimum(dy, h - dy)
    return np.sqrt(dx * dx + dy * dy)

MOON_PALETTE = [(0.0, 0x6E5A32), (0.30, 0x8A7444), (0.50, 0xAE965C), (0.68, 0xCDB577), (0.82, 0xE4CE92), (0.93, 0xF2E3AE)]

# ---- Moon cheese: pale lunar grey-gold, mottled, with dark craters/holes (a bit like actual cheese) -----------------
cheese_base = tile_noise(N, 4, 303) * 0.6 + tile_noise(N, 8, 304) * 0.4
cheese_img = palette(np.clip(0.45 + 0.4 * cheese_base, 0, 1), MOON_PALETTE)
cheese_craters = [(rng2.integers(0, N), rng2.integers(0, N), rng2.uniform(1.1, 2.4)) for _ in range(7)]
for (cx, cy, rad) in cheese_craters:
    d = wrapped_dist(xx, yy, cx, cy, N, wrap_y=True, h=N)
    hole, rim = d < rad, (d >= rad) & (d < rad + 1.0)
    cheese_img[hole] = cheese_img[hole] * 0.4 + hexrgb(0x4A3C20) * 0.4
    cheese_img[rim] = cheese_img[rim] * 0.55 + hexrgb(0xF6E8B4) * 0.55
os.makedirs(OUT + '/assets/shooting_star_addition/textures/block', exist_ok=True)
Image.fromarray(np.clip(cheese_img, 0, 255).astype(np.uint8), 'RGB').save(
    OUT + '/assets/shooting_star_addition/textures/block/moon_cheese.png')

# ---- Molten moon cheese: glowing orange-gold, veins of brighter lava pulsing and crawling along cracks --------------
molten_base_noise = tile_noise(N, 4, 305)
ridge_noise = tile_noise(N, 6, 306)
ridge = 1.0 - np.abs(ridge_noise * 2.0 - 1.0)              # thin bright ridges where the noise sits near 0.5
crack_mask = np.clip((ridge - 0.78) / 0.22, 0, 1) ** 1.5
molten_frames = []
for f in range(FRAMES):
    phase = f / FRAMES
    pulse = 0.55 + 0.45 * np.sin(2 * np.pi * (phase - molten_base_noise))
    base = 0.22 + 0.10 * molten_base_noise
    v = np.clip(base + crack_mask * (0.45 + 0.55 * pulse), 0, 1)
    img = palette(v, [(0.0, 0x431703), (0.28, 0x7E2A05), (0.46, 0xC1500A), (0.62, 0xF08A1E), (0.78, 0xFFC24D), (0.9, 0xFFF0B0)])
    molten_frames.append(img)
save_anim(OUT + '/assets/shooting_star_addition/textures/block/molten_moon_cheese.png', molten_frames)

# ---- SS-06 skill card: 64x32, a cracked moon falling through a red sky, same build as halley's card -----------------
Wc, Hc = 64, 32
luna_card = np.zeros((Hc, Wc, 3))
Yc, Xc = np.mgrid[0:Hc, 0:Wc]
sky_top2, sky_bot2 = hexrgb(0x2A0A08), hexrgb(0x7A1C10)
kk = (Yc / (Hc - 1))[..., None]
luna_card[:] = sky_top2 * (1 - kk) + sky_bot2 * kk
dither2 = ((Xc + Yc) % 2 == 0)[..., None]
luna_card = np.where(dither2, luna_card * 1.1, luna_card * 0.92)
for _ in range(20):
    sx, sy = rng2.integers(0, Wc), rng2.integers(0, 16)
    luna_card[sy, sx] = hexrgb(0xFFD9A0) if rng2.random() < 0.3 else hexrgb(0xC06848)
# the ground far below, lower edge, lit by the strike
gy = 27
luna_card[gy:, :] = luna_card[gy:, :] * 0.3 + hexrgb(0x1A0804) * 0.7
mx, my = 46.0, 29.0
for y in range(Hc):
    for x in range(Wc):
        g = np.exp(-(((x - mx) / 10.0) ** 2 + ((y - my) / 3.0) ** 2))
        if y >= gy - 1 and g > 0.1:
            luna_card[y, x] = luna_card[y, x] * (1 - 0.7 * g) + hexrgb(0xFFC23C) * 0.7 * g
# the moon itself: a cratered disc, cracked open, coming down toward the mark, with a streak trailing up behind it
mcx, mcy, mr = 30.0, 11.0, 9.0
dist2 = np.sqrt((Xc - mcx) ** 2 + (Yc - mcy) ** 2)
moon_disc = dist2 < mr
luna_card[moon_disc] = palette(np.clip(0.55 + 0.35 * ((Xc[moon_disc] + Yc[moon_disc]) % 3) / 3.0, 0, 1), MOON_PALETTE)
# shade the trailing (upper-left) limb darker, as if lit from the lower right where it is falling toward
shade = ((Xc - mcx) * -0.6 + (Yc - mcy) * -0.8) / mr
luna_card[moon_disc] = luna_card[moon_disc] * np.clip(1.0 - 0.45 * shade[moon_disc, None], 0.35, 1.15)
rim2 = (dist2 >= mr) & (dist2 < mr + 1.1)
luna_card[rim2] = luna_card[rim2] * 0.5 + hexrgb(0xFFE7B0) * 0.5
# molten cracks across its face
crack_dirs = [((-6.0, -2.0), (5.0, 3.0)), ((-3.0, 5.0), (4.0, -4.0)), ((2.0, -6.0), (-2.0, 6.0))]
for (d0, d1) in crack_dirs:
    for t in np.linspace(0.0, 1.0, 24):
        px = mcx + d0[0] * (1 - t) + d1[0] * t + rng2.uniform(-0.4, 0.4)
        py = mcy + d0[1] * (1 - t) + d1[1] * t + rng2.uniform(-0.4, 0.4)
        xi, yi = int(round(px)), int(round(py))
        if 0 <= xi < Wc and 0 <= yi < Hc and dist2[yi, xi] < mr:
            luna_card[yi, xi] = hexrgb(0xFFB33C) * 0.85 + luna_card[yi, xi] * 0.15
# the motion streak trailing up and away from it (where it came from)
for i in range(16):
    t = i / 16.0
    px, py = mcx - 7.0 - t * 14.0, mcy - 4.0 - t * 9.0
    xi, yi = int(round(px)), int(round(py))
    if 0 <= xi < Wc and 0 <= yi < Hc:
        fade = (1.0 - t) * 0.5
        luna_card[yi, xi] = luna_card[yi, xi] * (1 - fade) + hexrgb(0xFFA23C) * fade
os.makedirs(OUT + '/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote', exist_ok=True)
Image.fromarray(np.clip(luna_card, 0, 255).astype(np.uint8), 'RGB').save(
    OUT + '/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/luna.png')

# ---- Molten Might effect icon: 18x18, a golden-orange ember/heart glow ------------------------------------------------
E = 18
ey, ex = np.mgrid[0:E, 0:E]
ecx, ecy = (E - 1) / 2.0, (E - 1) / 2.0
edist = np.sqrt((ex - ecx) ** 2 + (ey - ecy) ** 2)
icon_rgb = palette(np.clip(1.0 - edist / 8.5, 0, 1) ** 1.3, [(0.0, 0x431703), (0.25, 0x9A3306), (0.45, 0xD9680E), (0.65, 0xFFA23C), (0.82, 0xFFD17A), (0.93, 0xFFF2CE)])
icon_alpha = np.clip(1.0 - edist / 8.7, 0.0, 1.0) ** 0.7 * 255.0
icon_alpha[edist > 8.7] = 0.0
icon_rgba = np.concatenate([icon_rgb, icon_alpha[..., None]], axis=2)
os.makedirs(OUT + '/assets/shooting_star_addition/textures/mob_effect', exist_ok=True)
Image.fromarray(np.clip(icon_rgba, 0, 255).astype(np.uint8), 'RGBA').save(
    OUT + '/assets/shooting_star_addition/textures/mob_effect/molten_might.png')

# ---- The sky moon's surface: equirectangular (2:1), grey-gold cheese with craters and maria, seamless in x ----------
Ws, Hs = 512, 256
ys_, xs_ = np.mgrid[0:Hs, 0:Ws]
surf_base = (tile_noise_rect(Ws, Hs, 32, 16, 401) * 0.5
             + tile_noise_rect(Ws, Hs, 64, 32, 402) * 0.3
             + tile_noise_rect(Ws, Hs, 16, 8, 403) * 0.2)
surface = palette(np.clip(0.4 + 0.45 * surf_base, 0, 1), MOON_PALETTE)
# maria: a handful of big dark blotches, like lunar seas
maria_field = tile_noise_rect(Ws, Hs, 10, 5, 404)
maria_mask = maria_field > 0.62
surface[maria_mask] = surface[maria_mask] * 0.55 + hexrgb(0x5A4A28) * 0.45
# craters: round holes of all sizes, dark with a bright rim, wrapping round the sides
for _ in range(90):
    cx_, cy_, rad_ = rng2.uniform(0, Ws), rng2.uniform(Hs * 0.08, Hs * 0.92), rng2.uniform(2.0, 14.0)
    d = wrapped_dist(xs_, ys_, cx_, cy_, Ws)
    hole, rim = d < rad_ * 0.72, (d >= rad_ * 0.72) & (d < rad_)
    surface[hole] = surface[hole] * 0.45 + hexrgb(0x453A20) * 0.35
    surface[rim] = surface[rim] * 0.6 + hexrgb(0xF2E0A8) * 0.45
os.makedirs(OUT + '/assets/shooting_star_addition/textures/luna', exist_ok=True)
Image.fromarray(np.clip(surface, 0, 255).astype(np.uint8), 'RGB').save(
    OUT + '/assets/shooting_star_addition/textures/luna/surface.png')

# ---- The sky moon's seams: bright molten crack network on a transparent field, seamless in x, used as an emissive mask --
seam_ridge_noise = (tile_noise_rect(Ws, Hs, 24, 12, 501) * 0.6
                     + tile_noise_rect(Ws, Hs, 48, 24, 502) * 0.4)
seam_ridge = 1.0 - np.abs(seam_ridge_noise * 2.0 - 1.0)
seam_mask = np.clip((seam_ridge - 0.83) / 0.17, 0, 1) ** 1.4
seam_rgb = palette(np.clip(0.5 + 0.5 * seam_mask, 0, 1), [(0.0, 0x431703), (0.4, 0xC1500A), (0.7, 0xFFA23C), (0.9, 0xFFEBB0)])
seam_alpha = np.clip(seam_mask * 255.0 * 1.4, 0, 255)
seam_rgba = np.concatenate([seam_rgb, seam_alpha[..., None]], axis=2)
Image.fromarray(np.clip(seam_rgba, 0, 255).astype(np.uint8), 'RGBA').save(
    OUT + '/assets/shooting_star_addition/textures/luna/seams.png')

# (the mod logo is the project thumbnail: see make_thumbnail.py)
print('art written to', OUT)
