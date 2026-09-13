// Utilidades para manejar el emulador (o un Android conectado) desde los scripts de QA.
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
import { join } from 'node:path';

export const ADB = process.env.ADB ?? join(homedir(), 'Library/Android/sdk/platform-tools/adb');
export const EXP = process.env.EXP_URL;
export const API = process.env.API ?? 'http://localhost:3100';
if (!EXP) throw new Error('Falta EXP_URL (la dirección exp:// que imprime `npx expo start`)');

export const adb = (...a) => execFileSync(ADB, a, { maxBuffer: 64 << 20 });
export const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const [W, H] = adb('shell', 'wm', 'size').toString().match(/(\d+)x(\d+)/).slice(1).map(Number);
const density = Number(adb('shell', 'wm', 'density').toString().match(/(\d+)\s*$/)[1]) / 160;
const k = density * Math.min(1, W / density / 390);
const statusPx = Number(adb('shell', 'dumpsys', 'window').toString().match(/type=statusBars frame=\[0,0\]\[\d+,(\d+)\]/)?.[1] ?? 0);
export const pantalla = { W, H, density };

// coordenadas de Figma (pt) → píxeles del teléfono; `desde` indica si el elemento se ancla arriba o abajo
const px = (x, y, desde = 'arriba') => [Math.round(x * k), desde === 'arriba' ? Math.round(statusPx + (y - 54) * k) : Math.round(H - (844 - y) * k)];

export const tocar = async (x, y, desde) => (adb('shell', 'input', 'touchscreen', 'tap', ...px(x, y, desde).map(String)), wait(900));
export const tocarDoble = async (x, y, desde) => {
  const p = px(x, y, desde).map(String);
  adb('shell', `input touchscreen tap ${p.join(' ')} & input touchscreen tap ${p.join(' ')}`);
  await wait(1200);
};
export const tocarDosALaVez = async (a, b) => {
  const pa = px(...a).map(String), pb = px(...b).map(String);
  adb('shell', `input touchscreen tap ${pa.join(' ')} & input touchscreen tap ${pb.join(' ')}`);
  await wait(1200);
};
export const mantener = async (x, y, desde) => {
  const p = px(x, y, desde).map(String);
  adb('shell', 'input', 'touchscreen', 'swipe', ...p, ...p, '900');
  await wait(900);
};
export const atras = async () => (adb('shell', 'input', 'keyevent', 'KEYCODE_BACK'), wait(900));
export const escribir = async (texto) => (adb('shell', 'input', 'text', texto.replace(/ /g, '%s')), wait(400));
export const abrir = async (ruta) => (adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', `'${EXP}/--/${ruta}'`, 'host.exp.exponent'), wait(1600));
export const captura = () => adb('exec-out', 'screencap', '-p');
export const estadoApi = (e = {}) => fetch(`${API}/_ejemplo/estado`, { method: 'POST', body: JSON.stringify(e) });
export const actividadArriba = () => adb('shell', 'dumpsys', 'activity', 'activities').toString().match(/topResumedActivity=.*?\{[^}]*\s(\S+\/\S+)/)?.[1] ?? '';
