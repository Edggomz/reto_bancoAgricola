// Captura cada vista en Android con la API de ejemplo → visual-qa/android/<vista>.png
//   EXP_URL=exp://<ip>:8081 node scripts/shoot-android.mjs [vista …]
import { mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { abrir, captura, estadoApi, tocar, wait } from './android.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const FRECUENCIA = 'frecuencia=quincena-fin-de-mes';
const APARTADO = 'credito=c-0452&partes=2';

const VISTAS = {
  '01': { ruta: '' },
  '02': { ruta: 'inicio' },
  '03': { ruta: 'que-dia' },
  '04': { ruta: `elige-fecha?${FRECUENCIA}` },
  '05': { ruta: `elige-fecha?${FRECUENCIA}`, pasos: async () => (await tocar(58, 276), await tocar(195, 784, 'abajo')) },
  '06': { ruta: 'que-cuota' },
  '07': { ruta: 'cuantas-partes?credito=c-0452' },
  '08': { ruta: `automatico?${APARTADO}` },
  '08a': { ruta: `abre-cuenta?${APARTADO}` },
  '08b': { ruta: `revisa-firma?${APARTADO}` },
  '09': { ruta: `automatico?${APARTADO}`, pasos: () => tocar(195, 784, 'abajo') },
  '10': { ruta: 'inicio', estado: { fecha: true, apartado: true } },
  '10b': { ruta: 'inicio', estado: { fecha: true } },
  '11': { ruta: 'avisos' },
  '12': { ruta: 'mi-record' },
  '13': { ruta: 'asesor' },
  '14': { ruta: 'asesor', pasos: () => tocar(102, 663, 'abajo') },
  '14b': { ruta: 'asesor?aviso=a-distinto' },
  '15': { ruta: 'asesor', pasos: async () => (await tocar(102, 663, 'abajo'), await wait(600), await tocar(360, 80)) },
};

const pedidas = process.argv.slice(2);
await mkdir(join(ROOT, 'visual-qa/android'), { recursive: true });
for (const k of pedidas.length ? pedidas : Object.keys(VISTAS)) {
  const v = VISTAS[k];
  await estadoApi(v.estado);
  // pasar por el menú de demostración obliga a abrir la vista de nuevo, con sus datos frescos
  await abrir('demo');
  await abrir(v.ruta);
  await wait(900);
  if (v.pasos) await v.pasos();
  await wait(500);
  await writeFile(join(ROOT, `visual-qa/android/${k}.png`), captura());
  process.stdout.write(`${k} `);
}
await estadoApi();
console.log('\nok');
