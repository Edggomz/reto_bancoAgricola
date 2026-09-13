// Una sola imagen con todas las parejas Figma | Android (visual-qa/android-lado),
// en el orden de las vistas → visual-qa/resumen-android.png
//   node scripts/resumen.mjs
import { execFileSync } from 'node:child_process';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DIR = join(ROOT, 'visual-qa/android-lado');
const ORDEN = ['01', '02', '02-B', '03', '04', '05', '06', '07', '08', '08a', '08b', '09', '10', '10b', '11', '12', '13', '14', '14b', '15'];
const presentes = new Set((await readdir(DIR)).map((f) => f.slice(0, -4)));
const keys = ORDEN.filter((k) => presentes.has(k));

const ancho = 420;
const pares = [];
for (const k of keys) {
  const out = join(tmpdir(), `resumen-${k}.png`);
  execFileSync('sips', ['--resampleWidth', String(ancho), join(DIR, `${k}.png`), '--out', out], { stdio: 'ignore' });
  pares.push(PNG.sync.read(await readFile(out)));
}
const cols = 4;
const gap = 16;
const h = Math.max(...pares.map((p) => p.height));
const rows = Math.ceil(pares.length / cols);
const hoja = new PNG({ width: cols * ancho + (cols - 1) * gap, height: rows * h + (rows - 1) * gap });
hoja.data.fill(255);
pares.forEach((p, i) => PNG.bitblt(p, hoja, 0, 0, p.width, p.height, (i % cols) * (ancho + gap), Math.floor(i / cols) * (h + gap)));
await writeFile(join(ROOT, 'visual-qa/resumen-android.png'), PNG.sync.write(hoja));
console.log(`visual-qa/resumen-android.png (${keys.length} vistas)`);
