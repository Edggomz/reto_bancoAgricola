// Recorta la misma región (en pt) de Figma y de la app, una encima de otra,
// para revisar de cerca alineaciones y tipografía.
//   node scripts/zoom.mjs <vista> <x> <y> <ancho> <alto>
// → visual-qa/zoom/<vista>-<x>-<y>.png  (arriba Figma, abajo app, a 2x)
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const [k, x, y, w, h] = process.argv.slice(2);
const [X, Y, W, H] = [x, y, w, h].map((v) => Math.round(Number(v) * 2));

const load = async (f) => PNG.sync.read(await readFile(join(ROOT, f)));
const ref = await load(`design/reference/${k}.png`);
const app = await load(`visual-qa/app/${k}.png`);

const gap = 6;
const out = new PNG({ width: W, height: H * 2 + gap });
out.data.fill(255);
for (let i = 0; i < W * gap; i++) out.data.writeUInt32BE(0xff0000ff, (W * H + i) * 4);
PNG.bitblt(ref, out, X, Y, W, H, 0, 0);
PNG.bitblt(app, out, X, Y, W, H, 0, H + gap);
await mkdir(join(ROOT, 'visual-qa/zoom'), { recursive: true });
const file = `visual-qa/zoom/${k}-${x}-${y}.png`;
await writeFile(join(ROOT, file), PNG.sync.write(out));
console.log(file);
