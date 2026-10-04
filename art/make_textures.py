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

# (the mod logo is the project thumbnail: see make_thumbnail.py)
print('art written to', OUT)
