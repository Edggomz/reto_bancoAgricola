// Captura cada vista de la app web a 390 × 844 @2x (el tamaño de design/reference)
// → visual-qa/app/<vista>.png. Usa la API de ejemplo para tener datos y toca la
// pantalla cuando la vista depende de un paso anterior (ventanas, chat).
//   node scripts/api-ejemplo/servidor.mjs
//   EXPO_PUBLIC_API_URL=http://<ip>:3100 npx expo start --web --port 8081
//   node scripts/shoot.mjs [vista …]
import puppeteer from 'puppeteer-core';
import { mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE ?? 'http://localhost:8081';
const API = process.env.API ?? 'http://localhost:3100';
const CHROME = process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SIN_API = !!process.env.SIN_API;

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const estado = (e = {}) => (SIN_API ? null : fetch(`${API}/_ejemplo/estado`, { method: 'POST', body: JSON.stringify(e) }));

async function tocar(page, texto) {
  const el = await page.waitForSelector(`xpath///*[normalize-space(text())=${JSON.stringify(texto)}]`, { timeout: 5000 });
  await el.click();
  await wait(700);
}
const tocarEtiqueta = async (page, label) => {
  await (await page.waitForSelector(`[aria-label=${JSON.stringify(label)}]`)).click();
  await wait(700);
};
async function escribirYEnviar(page, texto) {
  await page.type('input[placeholder="Escribe tu pregunta"]', texto);
  await tocarEtiqueta(page, 'Enviar');
  await wait(900);
}

const FRECUENCIA = 'frecuencia=quincena-fin-de-mes';
const APARTADO = 'credito=c-0452&partes=2';
export const VISTAS = {
  '01': { url: '/' },
  '02': { url: '/inicio' },
  '03': { url: '/que-dia' },
  '04': { url: `/elige-fecha?${FRECUENCIA}` },
  '05': { url: `/elige-fecha?${FRECUENCIA}`, pasos: async (p) => (await tocar(p, 'Día 18'), await tocar(p, 'CONFIRMAR EL DÍA 18')) },
  '06': { url: '/que-cuota' },
  '07': { url: '/cuantas-partes?credito=c-0452' },
  '08': { url: `/automatico?${APARTADO}` },
  '08a': { url: `/abre-cuenta?${APARTADO}` },
  '08b': { url: `/revisa-firma?${APARTADO}` },
  '09': { url: `/automatico?${APARTADO}`, pasos: (p) => tocar(p, 'ACTIVAR EN AUTOMÁTICO') },
  '10': { url: '/inicio', estado: { fecha: true, apartado: true } },
  '10b': { url: '/inicio', estado: { fecha: true } },
  '11': { url: '/avisos' },
  '12': { url: '/mi-record' },
  '13': { url: '/asesor' },
  '14': { url: '/asesor', pasos: (p) => escribirYEnviar(p, '¿Puedo mover mi cuota si este mes me pagan tarde?') },
  '14b': { url: '/asesor?aviso=a-distinto' },
  '15': { url: '/asesor', pasos: async (p) => (await escribirYEnviar(p, '¿Puedo mover mi cuota si este mes me pagan tarde?'), await tocarEtiqueta(p, 'Terminar conversación')) },
};

const pedidas = process.argv.slice(2);
const keys = pedidas.length ? pedidas : Object.keys(VISTAS);
const carpeta = SIN_API ? 'visual-qa/sin-api' : 'visual-qa/app';
await mkdir(join(ROOT, carpeta), { recursive: true });

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
for (const k of keys) {
  const v = VISTAS[k];
  await estado(v.estado);
  await page.goto(BASE + v.url, { waitUntil: 'networkidle0' });
  await page.evaluate(() => document.fonts.ready);
  await wait(900);
  if (v.pasos && !SIN_API) await v.pasos(page);
  await wait(400);
  await page.screenshot({ path: join(ROOT, `${carpeta}/${k}.png`) });
  process.stdout.write(`${k} `);
}
await estado();
await browser.close();
console.log('\nok');
