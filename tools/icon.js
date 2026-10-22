// BetterHotbarSwap kapağı: 64x64 sahne -> 4x en-yakın-komşu büyütme (256x256, bulanıklık yok).
// Kullanım: node tools/icon.js src/main/resources/assets/betterhotbarswap/icon.png [onizleme.png]
// Kazma dokuları vanilla'nın kendisi (tools/textures, 1.20.1 istemci jar'ından çıkarıldı).
const fs = require('fs'), zlib = require('zlib'), path = require('path');

// --- PNG okuma (8-bit RGBA / RGB / paletli + tRNS) ve yazma, bağımlılıksız.
function readPng(file) {
  const b = fs.readFileSync(file);
  let off = 8, w, h, depth, type, plte = null, trns = null; const idat = [];
  while (off < b.length) {
    const len = b.readUInt32BE(off), t = b.toString('ascii', off + 4, off + 8), d = b.subarray(off + 8, off + 8 + len);
    if (t === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; }
    else if (t === 'PLTE') plte = d; else if (t === 'tRNS') trns = d; else if (t === 'IDAT') idat.push(d);
    off += 12 + len;
  }
  if (depth !== 8) throw new Error(file + ': sadece 8-bit destekli');
  const bpp = { 2: 3, 3: 1, 6: 4 }[type]; if (!bpp) throw new Error(file + ': renk tipi ' + type);
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * bpp, out = Buffer.alloc(h * stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? out[y * stride + x - bpp] : 0, up = y ? out[(y - 1) * stride + x] : 0;
      const c = x >= bpp && y ? out[(y - 1) * stride + x - bpp] : 0;
      const p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c);
      const pred = [0, a, up, (a + up) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? up : c][f];
      out[y * stride + x] = (line[x] + pred) & 255;
    }
  }
  const px = [];
  for (let y = 0; y < h; y++) { px.push([]); for (let x = 0; x < w; x++) {
    const i = y * stride + x * bpp;
    if (type === 3) { const k = out[i]; px[y].push([plte[k * 3], plte[k * 3 + 1], plte[k * 3 + 2], trns && k < trns.length ? trns[k] : 255]); }
    else px[y].push([out[i], out[i + 1], out[i + 2], type === 6 ? out[i + 3] : 255]);
  } }
  return { w, h, px };
}
function writePng(px, scale) {
  const N = px.length, W = N * scale, raw = Buffer.alloc((W * 4 + 1) * W);
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) (px[Math.floor(y / scale)][Math.floor(x / scale)] || [0, 0, 0, 0]).forEach((v, i) => raw[y * (W * 4 + 1) + 1 + x * 4 + i] = v);
  const crcT = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = d => { let c = 0xFFFFFFFF; for (const v of d) c = crcT[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ih = Buffer.alloc(13); ih.writeUInt32BE(W, 0); ih.writeUInt32BE(W, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// --- Çizim yardımcıları (64x64 tuval).
const N = 64, px = Array.from({ length: N }, () => Array(N).fill(null));
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const set = (x, y, c) => { if (x >= 0 && x < N && y >= 0 && y < N) px[y][x] = c ? (typeof c === 'string' ? hex(c) : c) : null; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };
const art = (x0, y0, rows, pal) => rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) set(x0 + x, y0 + y, pal[ch]); }));
// Doku çiz; alpha < 1 ise alttakiyle karıştırır (hayalet kopya).
const sprite = (tex, x0, y0, alpha = 1) => tex.px.forEach((row, y) => row.forEach((c, x) => {
  if (c[3] < 128) return; const X = x0 + x, Y = y0 + y; if (X < 0 || X >= N || Y < 0 || Y >= N) return;
  const under = px[Y][X] || [0, 0, 0, 0];
  set(X, Y, [0, 1, 2].map(i => Math.round(under[i] * (1 - alpha) + c[i] * alpha)).concat(255));
}));
// Yarı saydam boyama (alttakiyle karışır).
const blend = (x, y, c, a) => { if (x < 0 || x >= N || y < 0 || y >= N || !px[y][x]) return;
  const u = px[y][x], k = hex(c); set(x, y, [0, 1, 2].map(i => Math.round(u[i] * (1 - a) + k[i] * a)).concat(255)); };
const slot = (x0, y0, x1, y1) => { rect(x0, y0, x1, y1, '#373737'); rect(x0 + 1, y0 + 1, x1, y1, '#FFFFFF'); rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, '#8B8B8B'); };
const outline = (x0, y0, x1, y1, fill, edge, bg) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
  const c = px[y][x]; if (!c || c.join() !== hex(fill).join()) continue;
  for (const [ax, ay] of [[x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]]) { const n = px[ay] && px[ay][ax]; if (n && n.join() === hex(bg).join()) set(ax, ay, edge); } } };

const tex = n => readPng(path.join(__dirname, 'textures', n + '.png'));
const wood = tex('wooden_pickaxe'), stone = tex('stone_pickaxe');

// Panel (vanilla envanter): siyah çerçeve, 2px beyaz/koyu kabartma, yuvarlak köşe.
rect(0, 0, 63, 63, '#000000'); rect(1, 1, 62, 62, '#C6C6C6');
rect(1, 1, 62, 2, '#FFFFFF'); rect(1, 1, 2, 62, '#FFFFFF');
rect(1, 61, 62, 62, '#555555'); rect(61, 1, 62, 62, '#555555');
for (const [x, y] of [[0, 0], [63, 0], [0, 63], [63, 63]]) set(x, y, null);
for (const [x, y] of [[1, 1], [62, 1], [1, 62], [62, 62]]) set(x, y, '#000000');

// Üst: vanilla üretim oku -> büyük çıktı slotu (taş kazma).
art(4, 11, ['        ##    ', '        ###   ', '############# ', '##############', '############# ', '        ###   ', '        ##    '], { '#': '#8B8B8B' });
slot(19, 4, 44, 29);
sprite(stone, 24, 9);
// Fare üstünde (vanilla hover beyazlaşması): vanilla gibi SADECE 16x16 eşya alanı, hafif %30.
for (let y = 9; y <= 24; y++) for (let x = 24; x <= 39; x++) blend(x, y, '#FFFFFF', 0.30);


// Yeşil ok: çıktı -> 2. slot. Gövde çıktı slotunun içinden, kazmanın hemen altından
// başlar (kullanıcı isteği 2026-10-22: "çizgisini çıktıdaki kazmaya doğru uzat").
art(28, 26, ['..####..', '..####..', '..####..', '..####..', '..####..', '..####..', '..####..', '..####..', '########', '.######.', '..####..', '...##...'], { '#': '#3ECF3E' });
for (const bg of ['#C6C6C6', '#8B8B8B', '#FFFFFF']) outline(26, 25, 37, 39, '#3ECF3E', '#1E6B1E', bg);

// Hotbar: 1 = kaymış tahta kazma, 2 = seçili + taş kazma, 3 = boş (referans görsel).
slot(4, 41, 21, 58); slot(23, 41, 40, 58); slot(42, 41, 59, 58);
rect(22, 40, 41, 40, '#FFFFFF'); rect(22, 59, 41, 59, '#FFFFFF'); rect(22, 40, 22, 59, '#FFFFFF'); rect(41, 40, 41, 59, '#FFFFFF');
// Kayma efekti (2 -> 1): bulanık hareket izi. 1px aralıklı 16 hayalet kopya, en uzaktaki
// en soluk (önce uzak çizilir); sık kopyalar birbirine karışıp yumuşak bulanıklık verir.
// Slot sınırının üstünden geçer. Taş kazma ve asıl tahta kazma üstte kalır.
for (let k = 16; k >= 1; k--) sprite(wood, 5 + k, 42, 0.5 * Math.pow(1 - k / 17, 1.2));
sprite(stone, 24, 42);
sprite(wood, 5, 42);
rect(7, 55, 19, 56, '#000000'); rect(7, 55, 14, 55, '#A5D62A');   // kullanılmış: dayanıklılık çubuğu

fs.writeFileSync(process.argv[2], writePng(px, 4));
if (process.argv[3]) fs.writeFileSync(process.argv[3], writePng(px, 8));
console.log('ok');
