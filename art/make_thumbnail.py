# The project thumbnail (CurseForge avatar / GitHub social image): 64x64 pixel art, written out at 512x512.
# Same style as the remote's skill cards - dithered night sky, Earth's limb - with SS-05's comet and an "Addon" tag.
import numpy as np
from PIL import Image
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'thumbnail.png')
# the same picture is the mod's logo in the game's mod list
LOGO = os.path.join(HERE, '..', 'src', 'main', 'resources', 'shooting_star_addition_icon.png')
rng = np.random.default_rng(64)
S = 64


def hexrgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


Y, X = np.mgrid[0:S, 0:S]
img = np.zeros((S, S, 3))
top, bottom = hexrgb(0x060A22), hexrgb(0x1E2A66)
k = (Y / (S - 1))[..., None]
img[:] = top * (1 - k) + bottom * k
img = np.where(((X + Y) % 2 == 0)[..., None], img * 1.1, img * 0.92)   # checker dither

# stars
for _ in range(46):
    sx, sy = rng.integers(0, S), rng.integers(0, 44)
    img[sy, sx] = hexrgb(0xFFFFFF) if rng.random() < 0.3 else hexrgb(0x9DB0E6)
for sx, sy in ((9, 14), (52, 8), (30, 5)):
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        img[sy + dy, sx + dx] = hexrgb(0xDCE6FF) if (dx, dy) == (0, 0) else hexrgb(0x7F93D0)

# Earth's limb along the bottom
cx, cy, r = 32.0, 150.0, 104.0
dist = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
earth = dist < r
img[earth] = hexrgb(0x1D58A8)
img[earth & ((X + Y) % 2 == 0) & (dist < r - 4)] = hexrgb(0x174A92)
img[earth & (dist > r - 3)] = hexrgb(0x2F8EE0)
glow = (dist >= r) & (dist < r + 1.4)
img[glow] = img[glow] * 0.35 + hexrgb(0x96DCFF) * 0.65

# the comet, tails streaming back to the upper left, coming down toward the limb
hx, hy = 40.0, 40.0


def streak(img, direction, length, w0, w1, colour, strength, bend=0.0, cut=0.18):
    d = np.array(direction, dtype=float)
    d /= np.linalg.norm(d)
    rx, ry = X - hx, Y - hy
    along = rx * d[0] + ry * d[1]
    across = rx * -d[1] + ry * d[0]
    a = np.clip(along, 0, None)
    off = across - bend * a * a
    width = w0 + (w1 - w0) * np.clip(a / length, 0, 1)
    body = np.exp(-(off / width) ** 2) * np.clip(1 - a / length, 0, 1) ** 0.9 * (along > -0.6)
    body = np.where(body < cut, 0, np.minimum(1, body * 1.25))
    return img * (1 - body[..., None] * strength) + hexrgb(colour) * body[..., None] * strength


tail = (-1.0, -0.82)
img = streak(img, (-1.0, -0.95), 44.0, 1.6, 6.0, 0x9FC8F0, 0.42, bend=0.003)   # dust tail
img = streak(img, tail, 58.0, 2.0, 4.0, 0x2E7FD8, 0.8)                        # ion tail glow
img = streak(img, tail, 46.0, 1.0, 1.6, 0x6FD8FF, 0.95)                        # its cyan core
img = streak(img, tail, 18.0, 0.7, 0.8, 0xE8FDFF, 1.0, cut=0.3)                # white-hot near the head
core = np.sqrt((X - hx) ** 2 + (Y - hy) ** 2)
img[core < 4.2] = img[core < 4.2] * 0.25 + hexrgb(0x6FE8FF) * 0.75
img[core < 2.6] = hexrgb(0xDFFBFF)
img[core < 1.2] = hexrgb(0xFFFFFF)
for d in range(3, 9):
    fade = 1.0 - (d - 3) / 6.0
    for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
        yy, xx = int(hy) + dy, int(hx) + dx
        if 0 <= yy < S and 0 <= xx < S:
            img[yy, xx] = img[yy, xx] * (1 - 0.85 * fade) + hexrgb(0xC8F6FF) * 0.85 * fade

# a cold glow on the limb ahead of it, where it will land
for y in range(S):
    for x in range(S):
        g = np.exp(-(((x - 50) / 6.0) ** 2 + ((y - 47.5) / 2.0) ** 2))
        if g > 0.12:
            img[y, x] = img[y, x] * (1 - 0.65 * g) + hexrgb(0xA8F2FF) * 0.65 * g

# the tag, bottom right, in the comet's ice-blue: "Addon"
FONT = {
    'A': ["0110", "1001", "1001", "1111", "1001", "1001"],
    'd': ["0001", "0001", "0111", "1001", "1001", "0111"],
    'o': ["0000", "0000", "0110", "1001", "1001", "0110"],
    'n': ["0000", "0000", "1110", "1001", "1001", "1001"],
}
text = "Addon"
tw = len(text) * 5 - 1
x0, y0 = S - tw - 6, S - 14
img[y0 - 2:y0 + 9, x0 - 3:x0 + tw + 3] = hexrgb(0x0E5B86)                # border
img[y0 - 1:y0 + 8, x0 - 2:x0 + tw + 2] = hexrgb(0x1FA8DC)                # face
img[y0 - 1, x0 - 2:x0 + tw + 2] = hexrgb(0x7FE0FF)                        # top highlight
img[y0 - 2, x0 - 3] = img[y0 - 2, x0 + tw + 2] = img[y0 + 8, x0 - 3] = img[y0 + 8, x0 + tw + 2] = img[y0 + 3, 0] * 0 + hexrgb(0x15205A)
for i, ch in enumerate(text):
    rows = FONT[ch]
    for ry, row in enumerate(rows):
        for rx, bit in enumerate(row):
            if bit == '1':
                img[y0 + 1 + ry, x0 + i * 5 + rx] = hexrgb(0xFFFFFF)
                img[y0 + 2 + ry, x0 + i * 5 + rx + 1] = np.minimum(img[y0 + 2 + ry, x0 + i * 5 + rx + 1], hexrgb(0x0E6A9A)) \
                    if FONT[ch][min(ry + 1, 5)][min(rx + 1, 3)] != '1' else img[y0 + 2 + ry, x0 + i * 5 + rx + 1]

picture = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
picture.resize((512, 512), Image.NEAREST).save(OUT)
picture.resize((256, 256), Image.NEAREST).save(LOGO)
print('thumbnail written to', OUT, 'and', os.path.normpath(LOGO))
