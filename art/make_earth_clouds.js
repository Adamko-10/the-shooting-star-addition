// Proxima's Earth clouds (art/make_earth.sh calls this): an equirectangular RGBA map, white with the clouds' cover in
// alpha, from domain-warped 3D value noise evaluated on the unit sphere, so it has no seam at the date line and no
// pinching at the poles. NASA's Blue Marble clouds image (the one The Shooting Star credits) is no longer online.
// Usage: node make_earth_clouds.js <out.png> [width]
const fs = require('fs');
const zlib = require('zlib');

const out = process.argv[2];
const W = Number(process.argv[3] || 2048);
const H = W / 2;

function hash(x, y, z) {
  let h = Math.imul(x, 374761393) ^ Math.imul(y, 668265263) ^ Math.imul(z, 2147483647);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
const fade = t => t * t * (3 - 2 * t);
function noise(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
  const xf = fade(x - xi), yf = fade(y - yi), zf = fade(z - zi);
  let v = 0;
  for (let c = 0; c < 8; c++) {
    const dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
    v += hash(xi + dx, yi + dy, zi + dz) * (dx ? xf : 1 - xf) * (dy ? yf : 1 - yf) * (dz ? zf : 1 - zf);
  }
  return v;
}
function fbm(x, y, z, octaves) {
  let v = 0, a = 0.5, f = 1, sum = 0;
  for (let o = 0; o < octaves; o++) {
    v += a * noise(x * f + o * 17.3, y * f - o * 9.1, z * f + o * 5.7);
    sum += a; a *= 0.5; f *= 2.03;
  }
  return v / sum;
}
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

const rows = Buffer.alloc((W * 4 + 1) * H);
for (let j = 0; j < H; j++) {
  const lat = Math.PI * (0.5 - (j + 0.5) / H);
  const cl = Math.cos(lat), sl = Math.sin(lat);
  rows[j * (W * 4 + 1)] = 0;
  for (let i = 0; i < W; i++) {
    const lon = Math.PI * 2 * ((i + 0.5) / W) - Math.PI;
    // a point on the sphere, stretched east-west a little (weather streams along the latitudes)
    const x = cl * Math.cos(lon) * 5.2, y = sl * 7.4, z = cl * Math.sin(lon) * 5.2;
    const wx = fbm(x + 3.1, y, z, 4) - 0.5, wy = fbm(x, y + 7.7, z, 4) - 0.5, wz = fbm(x, y, z + 1.9, 4) - 0.5;
    const n = fbm(x + 1.6 * wx, y + 1.6 * wy, z + 1.6 * wz, 6);
    // fewer clouds in the dry subtropics (about 25 degrees), more along the equator and the storm tracks
    const band = 0.06 * Math.cos(lat * 7.2) - 0.03;
    const cover = smooth(0.43, 0.63, n + band) * 0.96;
    const p = j * (W * 4 + 1) + 1 + i * 4;
    rows[p] = 255; rows[p + 1] = 255; rows[p + 2] = 255; rows[p + 3] = Math.round(cover * 255);
  }
}

function crc32(buf) {
  let c, crc = 0xFFFFFFFF;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xFF;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(rows, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
fs.writeFileSync(out, png);
let covered = 0;
for (let j = 0; j < H; j++) for (let i = 0; i < W; i++) covered += rows[j * (W * 4 + 1) + 1 + i * 4 + 3];
console.log(out, W + 'x' + H, (png.length / 1024).toFixed(0) + ' KB, mean cover ' + (covered / (W * H * 255)).toFixed(2));
