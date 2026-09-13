// Genera src/theme/tokens.ts desde lo exportado de Figma (design/tokens/*.json).
// No editar tokens.ts a mano: se vuelve a generar con
//   node scripts/gen-tokens.mjs
import { readFile, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = async (f) => JSON.parse(await readFile(join(ROOT, 'design/tokens', f), 'utf8'));

const camel = (s) => s.replace(/[-/](\w)/g, (_, c) => c.toUpperCase());
const variables = await read('figma-variables.json');
const textStyles = await read('text-styles.json');
const effectStyles = await read('effect-styles.json');

// Solo semánticos y medidas: los componentes nunca consumen primitivos.
const color = {};
const space = {};
const radius = {};
const size = {};
for (const v of variables) {
  const [group, ...rest] = v.name.split('/');
  if (v.collection.startsWith('1')) continue;
  if (group === 'color') {
    const [family, ...leaf] = rest;
    (color[family] ??= {})[camel(leaf.join('-'))] = v.value;
  }
  if (group === 'space') space[rest.join('')] = v.value;
  if (group === 'radius') radius[rest.join('')] = v.value;
  if (group === 'size') size[camel(rest.join('-'))] = v.value;
}

// Open Sans estático: en Android el peso va en la familia, no en fontWeight.
const FAMILY = {
  Light: 'OpenSans_300Light',
  Regular: 'OpenSans_400Regular',
  SemiBold: 'OpenSans_600SemiBold',
  Bold: 'OpenSans_700Bold',
};
const px = (value, fontSize) => (value.endsWith('%') ? (parseFloat(value) / 100) * fontSize : parseFloat(value));
const text = {};
for (const s of textStyles) {
  const weight = s.font.split(' ').pop();
  text[camel(s.name)] = {
    fontFamily: FAMILY[weight],
    fontSize: s.fontSize,
    lineHeight: px(s.lineHeight, s.fontSize),
    letterSpacing: Math.round(px(s.letterSpacing, s.fontSize) * 100) / 100,
  };
}

const hexA = (hex, a) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
};
const elevation = {};
for (const s of effectStyles) {
  const e = s.effects[0];
  elevation[s.name.split('/')[1]] = `${e.offset.x}px ${e.offset.y}px ${e.radius}px ${e.spread}px ${hexA(e.color, e.a)}`;
}

const body = `// GENERADO por scripts/gen-tokens.mjs desde Figma «Ruta · Sistema y Pantallas».
// No editar a mano: exportar de nuevo desde Figma y correr el script.

export const color = ${JSON.stringify(color, null, 2)} as const;

export const space = ${JSON.stringify(space, null, 2)} as const;

export const radius = ${JSON.stringify(radius, null, 2)} as const;

export const size = ${JSON.stringify(size, null, 2)} as const;

/** Los 15 estilos de texto. letterSpacing ya viene en px. */
export const text = ${JSON.stringify(text, null, 2)} as const;

/** elevation/1–3 como boxShadow. */
export const elevation = ${JSON.stringify(elevation, null, 2)} as const;

export type TextVariant = keyof typeof text;
`;
await writeFile(join(ROOT, 'src/theme/tokens.ts'), body.replace(/"(\w+)":/g, '$1:'));
console.log('src/theme/tokens.ts');
