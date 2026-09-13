// Pruebas de uso en Android: toques dobles, dos botones a la vez, botones inactivos,
// «atrás» del sistema y salidas de cada vista. Lee la pila de navegación del log
// (App.tsx la escribe en desarrollo) y compara con lo esperado.
//   EXP_URL=exp://<ip>:8081 node scripts/pruebas-android.mjs
import { abrir, adb, actividadArriba, atras, escribir, estadoApi, tocar, tocarDoble, tocarDosALaVez, wait } from './android.mjs';

// sin línea nueva en el log, la pila no cambió
let ultima = '';
const pila = () => {
  const lineas = adb('logcat', '-d', '-s', 'ReactNativeJS:I').toString().split('\n').filter((l) => l.includes('[ruta]'));
  if (lineas.length) ultima = lineas[lineas.length - 1].split('[ruta] ')[1].trim();
  return ultima;
};
const limpiarLog = () => adb('logcat', '-c');

// menú de demostración: reinicia la pila en una sola vista
const DEMO = { Ingreso: 0, Inicio: 1, QueDia: 2, QueCuota: 3, Avisos: 4, MiRecord: 5, Asesor: 6 };
async function empezarEn(vista, estado) {
  await estadoApi(estado);
  await abrir('demo');
  await tocar(195, 180 + 64 * DEMO[vista]);
  await wait(900);
  ultima = vista;
}

const resultados = [];
async function prueba(nombre, fn, esperado) {
  limpiarLog();
  let obtenido = '';
  try {
    await fn();
    await wait(700);
    obtenido = typeof esperado === 'string' && esperado === 'fuera' ? actividadArriba() : pila();
  } catch (e) {
    obtenido = `error: ${e.message}`;
  }
  const ok = esperado === 'fuera' ? !obtenido.includes('ExperienceActivity') : esperado.test(obtenido);
  resultados.push({ ok, nombre, obtenido });
  console.log(`${ok ? '✓' : '✗'} ${nombre}  →  ${obtenido}`);
}

const INICIO = [301, 501];
const CONTINUAR = [195, 784, 'abajo'];

await prueba('Ingreso: CONTINUAR sin usuario ni clave no avanza', async () => {
  await empezarEn('Ingreso');
  await tocar(...CONTINUAR);
}, /^Ingreso$/);

await prueba('Ingreso: con usuario y clave, dos toques en CONTINUAR abren un solo inicio', async () => {
  await tocar(195, 238);
  await escribir('edgar.gomez');
  await tocar(195, 320);
  await escribir('12345678');
  // «atrás» con el teclado abierto solo lo esconde
  await atras();
  await tocarDoble(...CONTINUAR);
  await wait(800);
}, /^Inicio$/);

await prueba('Inicio: «atrás» sale de la app (el ingreso ya no está debajo)', async () => {
  await atras();
}, 'fuera');

await prueba('03: CONTINUAR sin elegir no avanza', async () => {
  await empezarEn('Inicio');
  await tocar(...INICIO);
  await tocar(...CONTINUAR);
}, /QueDia$/);

await prueba('03 → 04: elige «Solo fin de mes» y continúa', async () => {
  await tocar(195, 330);
  await tocar(...CONTINUAR);
}, /QueDia > EligeFecha$/);

await prueba('04: CONFIRMAR sin elegir día no avanza', async () => {
  await tocar(...CONTINUAR);
}, /EligeFecha$/);

await prueba('05: tocar «Apartar» y «Ahora no» a la vez navega una sola vez', async () => {
  await tocar(58, 276);
  await tocar(...CONTINUAR);
  await wait(800);
  await tocarDosALaVez([195, 720, 'abajo'], [195, 784, 'abajo']);
  await wait(800);
}, /^(Inicio|Inicio > QueDia > EligeFecha > QueCuota)$/);

await prueba('06: dos toques en «Crédito personal» abren una sola vez 07', async () => {
  await empezarEn('QueCuota');
  await tocarDoble(195, 292);
}, /^QueCuota > CuantasPartes$/);

await prueba('07: CONTINUAR sin elegir partes no avanza', async () => {
  await tocar(...CONTINUAR);
}, /CuantasPartes$/);

await prueba('07 → 08: elige 2 partes y continúa (tiene cuenta de débito)', async () => {
  await tocar(65, 276);
  await tocar(...CONTINUAR);
  await wait(600);
}, /CuantasPartes > Automatico$/);

await prueba('08: «atrás» vuelve a 07', async () => {
  await atras();
}, /CuantasPartes$/);

await prueba('07: × cancela y vuelve al inicio', async () => {
  await tocar(360, 80);
  await wait(800);
}, /^Inicio$/);

await prueba('Sin cuenta de débito: 07 → 08a', async () => {
  await empezarEn('QueCuota', { debito: false });
  await tocar(195, 292);
  await tocar(65, 276);
  await tocar(...CONTINUAR);
  await wait(600);
}, /CuantasPartes > AbreCuenta$/);

await prueba('08a → 08b', async () => {
  await tocar(...CONTINUAR);
}, /AbreCuenta > RevisaFirma$/);

await prueba('08b: FIRMAR sin marcar la casilla no avanza', async () => {
  await tocar(...CONTINUAR);
}, /RevisaFirma$/);

await prueba('08b: marca la casilla, firma y vuelve a 08', async () => {
  await tocar(32, 599);
  await tocar(...CONTINUAR);
  await wait(600);
}, /RevisaFirma > Automatico$/);

await prueba('08: «Ahora no» lleva al inicio', async () => {
  await tocar(195, 728, 'abajo');
  await wait(800);
}, /^Inicio$/);

await prueba('11 abierto solo: ‹ lleva al inicio', async () => {
  await empezarEn('Avisos');
  await tocar(30, 80);
  await wait(800);
}, /Inicio$/);

await prueba('11: el aviso «Ya va la mitad» no lleva a ningún lado', async () => {
  await empezarEn('Avisos');
  await tocar(195, 384);
}, /^Avisos$/);

await prueba('11 → 12: aviso «Pagado, y a tiempo»', async () => {
  await tocar(195, 504);
}, /^Avisos > MiRecord$/);

await prueba('12: ‹ vuelve a los avisos', async () => {
  await tocar(30, 80);
}, /^Avisos$/);

await prueba('Asesor: dos toques en el botón flotante lo abren una vez', async () => {
  await empezarEn('Inicio');
  await tocarDoble(315, 768, 'abajo');
}, /^Inicio > Asesor$/);

await prueba('Asesor: enviar vacío no hace nada', async () => {
  await tocar(352, 788, 'abajo');
}, /^Inicio > Asesor$/);

await prueba('Asesor: ‹ vuelve al inicio y deja la conversación en pausa', async () => {
  await tocar(102, 663, 'abajo');
  await wait(1200);
  await tocar(30, 80);
}, /^Inicio$/);

await prueba('Asesor: × con conversación pregunta antes; tocar fuera cierra la pregunta', async () => {
  await tocar(315, 768, 'abajo');
  await wait(600);
  await tocar(360, 80);
  await tocar(195, 300);
}, /^Inicio > Asesor$/);

await prueba('Asesor: × y «Terminar» cierran la conversación', async () => {
  await tocar(360, 80);
  await tocar(195, 720, 'abajo');
}, /^Inicio$/);

await prueba('Asesor: «atrás» del sistema cierra la ventana', async () => {
  await tocar(315, 768, 'abajo');
  await wait(800);
  await atras();
}, /^Inicio$/);

await prueba('Tres «atrás» seguidos en medio del flujo no rompen nada', async () => {
  await tocar(...INICIO);
  await tocar(195, 290);
  await tocar(...CONTINUAR);
  await atras();
  await atras();
  await wait(400);
}, /^Inicio$/);

await estadoApi();
const fallas = resultados.filter((r) => !r.ok);
console.log(`\n${resultados.length - fallas.length}/${resultados.length} pruebas bien`);
process.exit(fallas.length ? 1 : 0);
