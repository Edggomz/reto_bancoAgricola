// API de ejemplo: responde el contrato de src/api con los datos ilustrativos de Figma,
// para probar la interfaz sin backend. No es el backend.
//   node scripts/api-ejemplo/servidor.mjs      → http://localhost:3100
//   EXPO_PUBLIC_API_URL=http://<ip-de-la-mac>:3100 npx expo start
import { createServer } from 'node:http';
import * as D from './datos.mjs';

const PORT = Number(process.env.PORT ?? 3100);
const RETRASO = Number(process.env.RETRASO ?? 350);

const inicial = () => ({ fecha: false, apartado: false, debito: true });
let estado = inicial();
const sesiones = new Map();

const inicio = () => ({
  ...D.inicioBase,
  credito: { ...D.inicioBase.credito, cambio: estado.fecha ? { dia: 18, desdeMes: 'octubre' } : null },
  ruta: estado.apartado
    ? { estado: 'activa', hitos: [{ fecha: '30 sep', detalle: 'Aparta $124.25', tipo: 'aparta' }, { fecha: '15 oct', detalle: 'Aparta $124.25', tipo: 'aparta' }, { fecha: '18 oct', detalle: 'Se paga $248.50', tipo: 'paga' }] }
    : estado.fecha
      ? { estado: 'solo-fecha', dia: 18 }
      : null,
  productos: estado.fecha ? [D.productos.deposito] : [D.productos.cambiarFecha, D.productos.deposito],
});

let n = 0;
const mensajes = (textos) => textos.map((texto) => ({ id: `m-${++n}`, rol: 'asesor', texto }));

const rutas = {
  'POST /auth/ingreso': () => ({ token: 'ejemplo' }),
  'GET /clientes/yo/inicio': inicio,
  'GET /fecha-cobro/opciones': (q) => D.opcionesFecha[q.get('frecuencia')] ?? D.opcionesFecha['quincena-fin-de-mes'],
  'POST /fecha-cobro': (_q, body) => {
    estado.fecha = true;
    return { dia: body.dia, desde: `${body.dia} de octubre de 2026`, operacion: '3242785' };
  },
  'GET /apartado/creditos': () => D.creditos,
  'GET /apartado/partes': () => D.partes,
  'GET /apartado/origen': () => (estado.debito ? D.origen : { ...D.origen, cuenta: null }),
  'POST /apartado': () => {
    estado.apartado = true;
    estado.fecha = true;
    return D.resumenApartado;
  },
  'GET /cuenta-digital/oferta': () => D.ofertaCuenta,
  'GET /cuenta-digital/contrato': () => D.contratoCuenta,
  'POST /cuenta-digital': () => {
    estado.debito = true;
    return undefined;
  },
  'GET /clientes/yo/record': () => D.record,
  'GET /clientes/yo/avisos': () => D.avisos(),
  'POST /dispositivos': () => undefined,
  'POST /asesor/sesiones': (_q, body) => {
    const id = `s-${Date.now()}`;
    sesiones.set(id, true);
    const g = body?.aviso ? D.asesor.desdeAviso : D.asesor.saludo;
    return { sesion: id, mensajes: mensajes(g.mensajes), sugerencias: g.sugerencias };
  },
  'POST /asesor/sesiones/:id/mensajes': () => ({ mensajes: mensajes(D.asesor.respuesta.mensajes), sugerencias: D.asesor.respuesta.sugerencias }),
  'POST /asesor/sesiones/:id/fin': () => undefined,
  // solo para las pruebas: fija el estado de la persona
  'POST /_ejemplo/estado': (_q, body) => {
    estado = { ...inicial(), ...body };
    return estado;
  },
};

const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': '*' };

createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return res.writeHead(204, cors).end();
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const clave = `${req.method} ${url.pathname.replace(/^\/api/, '').replace(/\/asesor\/sesiones\/[^/]+\//, '/asesor/sesiones/:id/')}`;
  const ruta = rutas[clave];
  let body = null;
  const trozos = [];
  for await (const t of req) trozos.push(t);
  if (trozos.length) body = JSON.parse(Buffer.concat(trozos).toString() || 'null');
  await new Promise((r) => setTimeout(r, url.pathname.startsWith('/_ejemplo') ? 0 : RETRASO));
  if (!ruta) return res.writeHead(404, cors).end('no existe');
  const r = ruta(url.searchParams, body);
  console.log(clave);
  if (r === undefined) return res.writeHead(204, cors).end();
  res.writeHead(200, { ...cors, 'Content-Type': 'application/json' }).end(JSON.stringify(r));
}).listen(PORT, () => console.log(`API de ejemplo en http://localhost:${PORT}`));
