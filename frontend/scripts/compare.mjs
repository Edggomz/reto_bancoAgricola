// Compara cada captura de la app contra la exportación de Figma, píxel a píxel.
//   node scripts/compare.mjs [vista …]
// Salidas: visual-qa/diff/<vista>.png (rojo = distinto) y
//          visual-qa/lado-a-lado/<vista>.png (Figma | app | diferencia, a 1x).
// La franja de la barra de estado (54 pt) no se compara: en el teléfono es la del sistema.
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const REF = join(ROOT, 'design/reference');
const APP = join(ROOT, 'visual-qa/app');
const STATUS = 54 * 2;

const load = async (f) => PNG.sync.read(await readFile(f));

/** Aplana sobre blanco (las esquinas redondeadas del marco vienen transparentes). */
function flatten(png) {
  const d = png.data;
  for (let i = 0; i < d.length; i += 4) {
    const a = d[i + 3] / 255;
    d[i] = Math.round(d[i] * a + 255 * (1 - a));
    d[i + 1] = Math.round(d[i + 1] * a + 255 * (1 - a));
    d[i + 2] = Math.round(d[i + 2] * a + 255 * (1 - a));
    d[i + 3] = 255;
  }
  return png;
}

function maskStatus(png) {
  png.data.fill(255, 0, STATUS * png.width * 4);
  return png;
}

function half(src) {
  const w = src.width >> 1;
  const h = src.height >> 1;
  const out = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++)
      for (let c = 0; c < 4; c++) {
        const p = (yy, xx) => src.data[((yy * src.width + xx) << 2) + c];
        out.data[((y * w + x) << 2) + c] = (p(2 * y, 2 * x) + p(2 * y, 2 * x + 1) + p(2 * y + 1, 2 * x) + p(2 * y + 1, 2 * x + 1)) >> 2;
      }
  return out;
}

function sideBySide(imgs) {
  const gap = 8;
  const w = imgs.reduce((s, i) => s + i.width, 0) + gap * (imgs.length - 1);
  const h = Math.max(...imgs.map((i) => i.height));
  const out = new PNG({ width: w, height: h });
  out.data.fill(200);
  let x0 = 0;
  for (const img of imgs) {
    PNG.bitblt(img, out, 0, 0, img.width, img.height, x0, 0);
    x0 += img.width + gap;
  }
  return out;
}

await mkdir(join(ROOT, 'visual-qa/diff'), { recursive: true });
await mkdir(join(ROOT, 'visual-qa/lado-a-lado'), { recursive: true });

const pedidas = process.argv.slice(2);
const keys = pedidas.length ? pedidas : (await readdir(APP)).filter((f) => f.endsWith('.png')).map((f) => f.slice(0, -4)).sort();
const rows = [];
for (const k of keys) {
  if (!existsSync(join(REF, `${k}.png`)) || !existsSync(join(APP, `${k}.png`))) continue;
  const ref = maskStatus(flatten(await load(join(REF, `${k}.png`))));
  const app = maskStatus(flatten(await load(join(APP, `${k}.png`))));
  if (ref.width !== app.width || ref.height !== app.height) {
    rows.push(`${k.padEnd(4)}  tamaños distintos ${ref.width}×${ref.height} vs ${app.width}×${app.height}`);
    continue;
  }
  const diff = new PNG({ width: ref.width, height: ref.height });
  const n = pixelmatch(ref.data, app.data, diff.data, ref.width, ref.height, { threshold: 0.1, includeAA: false, alpha: 0.25 });
  const pct = (100 * n) / (ref.width * (ref.height - STATUS));
  await writeFile(join(ROOT, `visual-qa/diff/${k}.png`), PNG.sync.write(diff));
  await writeFile(join(ROOT, `visual-qa/lado-a-lado/${k}.png`), PNG.sync.write(sideBySide([half(ref), half(app), half(diff)])));
  rows.push(`${k.padEnd(4)}  ${pct.toFixed(2).padStart(6)} % distinto`);
}
console.log(rows.join('\n'));
