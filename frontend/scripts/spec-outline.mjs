// Convierte design/specs/*.json en un esquema legible (design/specs/outline/*.txt):
// una línea por capa con layout, tamaño, tokens y texto.
//   node scripts/spec-outline.mjs
import { readdir, readFile, mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SPECS = join(ROOT, 'design/specs');
const OUT = join(SPECS, 'outline');

const paint = (p) => p.map((f) => {
  if (f.type === 'IMAGE') return `img(${f.scaleMode})`;
  if (f.type !== 'SOLID') return f.type.toLowerCase();
  return `${f.token ?? '⚠'}${f.token ? '' : f.hex}${f.opacity ? `@${f.opacity}` : ''}`;
}).join('+');

function line(n) {
  const bits = [];
  const size = `${n.w}×${n.h} @${n.x},${n.y}`;
  if (n.type === 'TEXT') {
    const txt = JSON.stringify(n.text.length > 90 ? n.text.slice(0, 90) + '…' : n.text);
    bits.push(`T ${txt}`, n.textStyle ?? '⚠sin-estilo', `${n.font} ${n.fontSize}/${n.lineHeight}${n.letterSpacing ? ` ls${n.letterSpacing}` : ''}`);
    if (n.textCase) bits.push(n.textCase);
    if (n.decoration) bits.push(n.decoration);
    if (n.fills) bits.push(paint(n.fills));
    bits.push(`align:${n.align.join('/')}`, `auto:${n.autoResize}`, size);
    if (n.segments) bits.push('segs:' + n.segments.map((s) => `[${JSON.stringify(s.text)} ${s.style ?? s.font} ${s.fills ? paint(s.fills) : ''}${s.decoration ? ' ' + s.decoration : ''}]`).join(''));
  } else {
    bits.push(`${n.type} "${n.name}"`, size);
    if (n.component) bits.push(`⟨${n.component}⟩`);
    if (n.layout) {
      const l = n.layout;
      bits.push(`${l.mode[0]} pad[${l.pad.join(',')}] gap${l.gap} main:${l.main} cross:${l.cross}${l.wrap ? ` wrap crossGap${l.crossGap}` : ''}${l.strokesInLayout ? ' strokesInLayout' : ''}`);
    }
    if (n.fills) bits.push(`fill:${paint(n.fills)}`);
    if (n.strokes) bits.push(`stroke:${paint(n.strokes)} ${Array.isArray(n.strokeWeight) ? n.strokeWeight.join(',') : n.strokeWeight}${n.strokeAlign ? ' ' + n.strokeAlign.toLowerCase() : ''}${n.dash ? ' dash' + n.dash.join(',') : ''}`);
    if (n.radius) bits.push(`r${Array.isArray(n.radius) ? n.radius.join(',') : n.radius}`);
    if (n.effect) bits.push(`fx:${n.effect}`);
    if (n.effects) bits.push('fx:' + JSON.stringify(n.effects));
    if (n.clip) bits.push('clip');
    if (n.note) bits.push(`(${n.note})`);
  }
  if (n.sizing) bits.push(`sz:${n.sizing.map((s) => s[0]).join('/')}`);
  if (n.absolute) bits.push('ABS');
  if (n.opacity) bits.push(`op${n.opacity}`);
  if (n.hidden) bits.push('HIDDEN');
  if (n.minmax) bits.push('minmax' + JSON.stringify(n.minmax));
  if (n.tok) bits.push('tok{' + Object.entries(n.tok).map(([k, v]) => `${k}=${v}`).join(' ') + '}');
  if (n.reactions) bits.push('→ ' + n.reactions.map((r) => `${r.nav}${r.dest ? ':' + r.dest : ''}${r.transition ? ` ${r.transition.type}${r.transition.dir ? '-' + r.transition.dir : ''} ${r.transition.ms}ms` : ''}`).join(', '));
  return bits.join(' | ');
}

function walk(n, depth, out) {
  out.push('  '.repeat(depth) + line(n));
  for (const c of n.children ?? []) walk(c, depth + 1, out);
}

await mkdir(OUT, { recursive: true });
for (const dir of [SPECS, join(SPECS, 'componentes')]) {
  for (const f of (await readdir(dir)).filter((x) => x.endsWith('.json'))) {
    const spec = JSON.parse(await readFile(join(dir, f), 'utf8'));
    const out = [];
    walk(spec, 0, out);
    await writeFile(join(OUT, f.replace('.json', '.txt')), out.join('\n') + '\n');
  }
}
console.log('ok');
