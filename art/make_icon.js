// The mod icon (mod list logo, CurseForge avatar): 64x64 pixel art written out at 256x256, nearest neighbour.
// The four SA strikes around Earth: SA-01 Halley's comet from the top left, SA-04 Proxima (a red dwarf) at the top,
// SA-02 Luna (the full Moon, cracking) at the top right, their paths meeting at SA-00 Stellar Convergence's point on
// the limb, and an "SA" badge over the old icon's "Addon" tag. Same style as the old icon (art/make_thumbnail.py).
// Run: node art/make_icon.js [out.png]   (Node 22+, no npm packages)
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib'), assert = require('assert');

const S = 64, SCALE = 4;
const OUT = process.argv[2] || path.join(__dirname, '..', 'src', 'main', 'resources', 'shooting_star_addition_icon.png');

let seed = 64;
function rand() { // mulberry32, so the stars land in the same places every run
  seed = (seed + 0x6D2B79F5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

const rgb = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
const img = Array.from({ length: S * S }, () => [0, 0, 0]);
function blend(x, y, hex, a) {
  x = Math.round(x); y = Math.round(y);
  if (x < 0 || y < 0 || x >= S || y >= S || a <= 0) return;
  a = Math.min(1, a);
  const p = img[y * S + x], c = rgb(hex);
  for (let i = 0; i < 3; i++) p[i] = p[i] * (1 - a) + c[i] * a;
}
const put = (x, y, hex) => blend(x, y, hex, 1);
const each = f => { for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) f(x, y); };
// a little pixel map: each character is a colour from the key, '.' is left alone
const stamp = (x0, y0, rows, key) => rows.forEach((row, dy) => [...row].forEach((ch, dx) => key[ch] !== undefined && put(x0 + dx, y0 + dy, key[ch])));

// the night sky, dithered like the remote's skill cards
const skyTop = rgb(0x060A22), skyBottom = rgb(0x1E2A66);
each((x, y) => {
  const k = y / (S - 1), d = (x + y) % 2 === 0 ? 1.1 : 0.92;
  img[y * S + x] = skyTop.map((t, i) => (t * (1 - k) + skyBottom[i] * k) * d);
});

// where the three strikes' paths meet: SA-00 Stellar Convergence's point, on the limb just left of centre
const CX = 31, CY = 46;
const EX = 32, EY = 150, ER = 104;               // Earth: a big circle whose top shows along the bottom
const earthDist = (x, y) => Math.hypot(x - EX, y - EY);

// stars (before everything else, so the strikes cover them)
for (let n = 0; n < 32; n++) {
  const x = Math.floor(rand() * S), y = Math.floor(rand() * 44), white = rand() < 0.3;
  if (earthDist(x, y) < ER + 3 || Math.hypot(x - CX, y - CY) < 10) continue;
  put(x, y, white ? 0xFFFFFF : 0x9DB0E6);
}
for (const [x, y] of [[7, 39], [58, 31], [38, 3]]) {      // a few twinkling ones
  put(x, y, 0xDCE6FF);
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) put(x + dx, y + dy, 0x7F93D0);
}

// Earth: ocean, a lighter rim, the thin glowing atmosphere above it
each((x, y) => {
  const d = earthDist(x, y);
  if (d < ER) {
    put(x, y, d > ER - 3 ? 0x2F8EE0 : (x + y) % 2 === 0 && d < ER - 4 ? 0x174A92 : 0x1D58A8);
  } else if (d < ER + 1.4) blend(x, y, 0x96DCFF, 0.65);
});
const LAND = { g: 0x3A9248, G: 0x2B7139, b: 0x9A7A4C };
stamp(2, 54, ['..gggg.....', 'gggGGgbbg..', '.ggGgg..gg.', '...g.......'], LAND);   // flattened: we see them edge-on
stamp(17, 58, ['.gg....', 'ggGbgg.', '..gggg.'], LAND);

// a soft streak with a gaussian cross-section, fading out along its length (the comet's tails)
function streak(hx, hy, dx, dy, len, w0, w1, hex, strength, bend = 0, cut = 0.18) {
  const n = Math.hypot(dx, dy); dx /= n; dy /= n;
  each((x, y) => {
    const rx = x - hx, ry = y - hy, along = rx * dx + ry * dy, a = Math.max(0, along);
    const off = (-rx * dy + ry * dx) - bend * a * a, w = w0 + (w1 - w0) * Math.min(1, a / len);
    let body = Math.exp(-((off / w) ** 2)) * Math.max(0, 1 - a / len) ** 0.9 * (along > -0.6 ? 1 : 0);
    if (body >= cut) blend(x, y, hex, Math.min(1, body * 1.25) * strength);
  });
}
// a strike's path down to the point, in stepped colours rather than fades (fades go muddy on the dark sky): dotted
// at first (so it isn't a string tied to the object), then solid and brighter, with a 1px glow either side, then white
function path_(x0, y0, mid, bright) {
  const steps = Math.max(Math.abs(CX - x0), Math.abs(CY - y0)), [px, py] = Math.abs(CX - x0) > Math.abs(CY - y0) ? [0, 1] : [1, 0];
  for (let i = 0; i <= steps; i++) {
    const t = i / steps, x = x0 + (CX - x0) * t, y = y0 + (CY - y0) * t;
    if (t < 0.3 || (t < 0.5 && i % 2)) continue;
    put(x, y, t > 0.84 ? 0xF2E8FF : t > 0.66 ? bright : mid);
    if (t > 0.66) { blend(x + px, y + py, bright, 0.3); blend(x - px, y - py, bright, 0.3); }
  }
}

// SA-02 Luna, top right: the full Moon as you'd know it in the sky, two molten cracks where it's tearing loose
const MX = 49, MY = 14, MR = 9.3;
each((x, y) => {   // a soft pale glow round it
  const d = Math.hypot(x - MX, y - MY);
  if (d >= MR && d < MR + 3.5) blend(x, y, 0xBFCBEA, 0.45 * (1 - (d - MR) / 3.5) ** 1.5);
});
each((x, y) => {   // silvery grey-white, a touch darker toward the lower right so it reads as a ball
  const dx = x - MX, dy = y - MY, d = Math.hypot(dx, dy);
  if (d >= MR) return;
  const l = (-dx * 0.5 - dy * 0.7) / MR;
  put(x, y, d > MR - 1.3 && l < 0.1 ? 0xC4C8D0 : l > 0.35 ? 0xF0F2F5 : 0xDEE1E6);
});
// the maria, as seen from the northern hemisphere: Imbrium and Serenitatis (the eyes), Tranquillitatis, Crisium out on
// the right, Procellarum down the left, Nubium and Humorum below; then Copernicus, Tycho and a few small crater rims
stamp(MX - 9, MY - 9, [
  '...................',
  '...................',
  '......ffffff.......',
  '....mmmm.f.....o...',
  '...mmMMmm.mmm...r..',
  '..mmMMMmm.mmmm.mm..',
  '.mmmmMmmm.mmmm.mm..',
  '.mmmmm.mm..mmmm....',
  '.mmmm..o...mmMm....',
  '.mmmm..r....mmmm...',
  '.mmm.........mmm...',
  '..mm..mmm..mm.mm...',
  '...mm.mmmm....m....',
  '...mm..mm..........',
  '...................',
  '........o..........',
  '........r..o.......',
  '............r......',
  '...................',
], { f: 0xBDC1CA, m: 0x9DA2AE, M: 0x878D9A, o: 0xFFFFFF, r: 0xB0B4BE });
// the cracks start at the limb facing Earth, hottest there
const CRACKS = [
  [[42, 20], [43, 19], [44, 19], [45, 18], [46, 18], [47, 17], [48, 16]],
  [[43, 21], [44, 21], [45, 21], [46, 20]],
];
CRACKS.forEach(crack => crack.forEach(([x, y], i) => put(x, y, i < 2 ? 0xFFD060 : i < crack.length - 1 ? 0xFF8A2A : 0xE86A24)));

// SA-04 Proxima, top: a small crimson red dwarf in a magenta glow
const PX = 29, PY = 9;
each((x, y) => {
  const d = Math.hypot(x - PX, y - PY);
  if (d < 7) blend(x, y, 0xC81E66, 0.75 * (1 - d / 7) ** 2);
  if (d < 2.6) put(x, y, 0xE8283C);
  if (d < 1.6) put(x, y, 0xFF5A66);
});
put(PX, PY, 0xFFC0C6);
for (let d = 3; d <= 5; d++) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
  blend(PX + dx * d, PY + dy * d, 0xFF5A82, 0.9 * (1 - (d - 3) / 3));
}

// SA-01 Halley, top left: the icy comet, tails streaming back the way it came
const HX = 15, HY = 25, TX = HX - CX, TY = HY - CY;    // tail: straight back along its path
streak(HX, HY, -0.53, -0.85, 28, 1.4, 5.0, 0x9FC8F0, 0.42, 0.003);   // dust tail
streak(HX, HY, TX, TY, 36, 2.0, 3.6, 0x2E7FD8, 0.8);                // ion tail glow
streak(HX, HY, TX, TY, 30, 1.0, 1.5, 0x6FD8FF, 0.95);               // its cyan core
streak(HX, HY, TX, TY, 12, 0.7, 0.8, 0xE8FDFF, 1.0, 0, 0.3);        // white-hot near the head

// the three paths, down to the point
path_(HX, HY, 0x3FA8E0, 0x8FE8FF);
path_(PX, PY, 0xC0284A, 0xFF5A78);
path_(MX, MY, 0xC85A1E, 0xFFA040);

// Halley's head, over its path
each((x, y) => {
  const d = Math.hypot(x - HX, y - HY);
  if (d < 3.4) blend(x, y, 0x6FE8FF, 0.75);
  if (d < 2.2) put(x, y, 0xDFFBFF);
  if (d < 1.0) put(x, y, 0xFFFFFF);
});
for (let d = 3; d <= 6; d++) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
  blend(HX + dx * d, HY + dy * d, 0xC8F6FF, 0.85 * (1 - (d - 3) / 4));
}

// SA-00 Stellar Convergence: a white-violet sparkle where they meet, its glow spread along the limb
each((x, y) => {
  const g = Math.exp(-(((x - CX) / 8) ** 2) - ((y - CY) / 3) ** 2), h = Math.exp(-((Math.hypot(x - CX, y - CY) / 7) ** 2));
  if (h > 0.1) blend(x, y, 0x8A6CFF, 0.3 * h);
  if (g > 0.1) blend(x, y, 0xB89CFF, 0.8 * g);
});
for (let d = 1; d <= 10; d++) {
  const a = 1 - (d - 1) / 10, hex = d < 4 ? 0xF4EEFF : 0xC4A8FF;
  blend(CX + d, CY, hex, a); blend(CX - d, CY, hex, a);
  if (d <= 7) blend(CX, CY - d, hex, a * 1.1);
  if (d <= 4) blend(CX, CY + d, hex, a);
  if (d <= 2) for (const [dx, dy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) blend(CX + dx * d, CY + dy * d, 0xD8C8FF, 0.8 / d);
}
for (const [dx, dy] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) put(CX + dx, CY + dy, 0xFFFFFF);

// the old icon's "Addon" tag along the bottom right, drawn just as art/make_thumbnail.py drew it (moved right and down
// to clear the sparkle)
const TAG = {
  A: ['0110', '1001', '1001', '1111', '1001', '1001'],
  d: ['0001', '0001', '0111', '1001', '1001', '0111'],
  o: ['0000', '0000', '0110', '1001', '1001', '0110'],
  n: ['0000', '0000', '1110', '1001', '1001', '1001'],
};
const TX0 = 36, TY0 = 54, TW = 24;
for (let y = TY0 - 2; y <= TY0 + 8; y++) for (let x = TX0 - 3; x <= TX0 + TW + 2; x++) {
  const border = y === TY0 - 2 || y === TY0 + 8 || x === TX0 - 3 || x === TX0 + TW + 2;
  put(x, y, border ? 0x0E5B86 : y === TY0 - 1 ? 0x7FE0FF : 0x1FA8DC);
}
for (const x of [TX0 - 3, TX0 + TW + 2]) for (const y of [TY0 - 2, TY0 + 8]) put(x, y, 0x15205A);
[...'Addon'].forEach((ch, i) => TAG[ch].forEach((row, ry) => [...row].forEach((bit, rx) => {
  if (bit !== '1') return;
  const x = TX0 + i * 5 + rx, y = TY0 + 1 + ry;
  put(x, y, 0xFFFFFF);
  if (TAG[ch][Math.min(ry + 1, 5)][Math.min(rx + 1, 3)] !== '1') {   // its dark drop shadow
    const p = img[(y + 1) * S + x + 1];
    rgb(0x0E6A9A).forEach((c, k) => { p[k] = Math.min(p[k], c); });
  }
})));

// the SA badge sitting on top of the tag, right-aligned: a rounded light-blue plate with "SA" in chunky dark letters
const FONT = {
  S: ['.#####', '######', '##....', '#####.', '.#####', '....##', '######', '#####.'],
  A: ['.####.', '######', '##..##', '##..##', '######', '######', '##..##', '##..##'],
};
const BW = 20, BH = 14, BX = 43, BY = 39;   // letters start on even pixels, so a 32x32 downscale keeps them sharp
for (let y = BY + 1; y < TY0 - 2; y++) for (let x = BX + 1; x <= BX + BW; x++) blend(x, y, 0x000000, 0.35);  // shadow, not on the tag
for (let y = BY; y < BY + BH; y++) for (let x = BX; x < BX + BW; x++) {
  const edgeX = x === BX || x === BX + BW - 1, edgeY = y === BY || y === BY + BH - 1;
  if (edgeX && edgeY) continue;                                                        // rounded corners
  const nearCorner = (x <= BX + 1 || x >= BX + BW - 2) && (y <= BY + 1 || y >= BY + BH - 2);
  put(x, y, edgeX || edgeY || nearCorner ? 0x0A1F4A : y === BY + 1 ? 0xD4F2FF : y === BY + BH - 2 ? 0x5FB2E6 : 0x8FD6FF);
}
[...'SA'].forEach((ch, i) => stamp(BX + 3 + i * 8, BY + 3, FONT[ch], { '#': 0x0B1B45 }));

// write it out, scaled up
const W = S * SCALE, raw = Buffer.alloc((W * 4 + 1) * W);
for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
  const p = img[Math.floor(y / SCALE) * S + Math.floor(x / SCALE)], o = y * (W * 4 + 1) + 1 + x * 4;
  for (let i = 0; i < 3; i++) raw[o + i] = Math.max(0, Math.min(255, Math.round(p[i])));
  raw[o + 3] = 255;
}
function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]), out = Buffer.alloc(body.length + 8);
  out.writeUInt32BE(data.length, 0); body.copy(out, 4); out.writeUInt32BE(zlib.crc32(body), body.length + 4);
  return out;
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(W, 4); ihdr[8] = 8; ihdr[9] = 6;   // 8-bit RGBA, filter 0 on every row
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
// self-check: the IDAT inflates back to the rows we wrote
assert.deepStrictEqual(zlib.inflateSync(png.subarray(33 + 8, png.length - 12 - 4)), raw);
fs.writeFileSync(OUT, png);
console.log('icon written to', path.normalize(OUT));
