// Pone lado a lado Figma (390 pt) y la captura de Android llevada a dp (1 dp = 1 pt).
// No es una diferencia de píxeles: el S23 Ultra mide 384 dp de ancho, no 390.
//   node scripts/android-side.mjs [vista …]  → visual-qa/android-lado/<vista>.png
import { execFileSync } from 'node:child_process';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DENSITY = Number(process.env.DENSITY ?? 2.8125); // 450 dpi / 160

const resized = async (src, width) => {
  const out = join(tmpdir(), `ruta-${width}-${src.split('/').pop()}`);
  execFileSync('sips', ['--resampleWidth', String(width), src, '--out', out], { stdio: 'ignore' });
  return PNG.sync.read(await readFile(out));
};

await mkdir(join(ROOT, 'visual-qa/android-lado'), { recursive: true });
const pedidas = process.argv.slice(2);
const keys = pedidas.length ? pedidas : (await readdir(join(ROOT, 'visual-qa/android'))).filter((f) => /^\d/.test(f)).map((f) => f.slice(0, -4)).sort();
for (const k of keys) {
  const refFile = join(ROOT, `design/reference/${k}.png`);
  const andFile = join(ROOT, `visual-qa/android/${k}.png`);
  if (!existsSync(refFile) || !existsSync(andFile)) continue;
  const android = PNG.sync.read(await readFile(andFile));
  const ref = await resized(refFile, 390);
  const and = await resized(andFile, Math.round(android.width / DENSITY));
  const gap = 12;
  const out = new PNG({ width: ref.width + gap + and.width, height: Math.max(ref.height, and.height) });
  out.data.fill(210);
  PNG.bitblt(ref, out, 0, 0, ref.width, ref.height, 0, 0);
  PNG.bitblt(and, out, 0, 0, and.width, and.height, ref.width + gap, 0);
  await writeFile(join(ROOT, `visual-qa/android-lado/${k}.png`), PNG.sync.write(out));
}
console.log('ok');
