import { pedir } from './cliente';
import type {
  Aviso,
  ContratoCuenta,
  CreditoApartable,
  FechaConfirmada,
  Frecuencia,
  Inicio,
  OfertaCuenta,
  OpcionesFecha,
  OpcionesPartes,
  OrigenApartado,
  Producto,
  Record,
  RespuestaAsesor,
  Resumen,
  SesionAsesor,
  Sugerencia,
} from './tipos';

const q = encodeURIComponent;
const query = (params: { [clave: string]: string | number | undefined | null }) => {
  const partes = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${q(k)}=${q(String(v))}`);
  return partes.length ? `?${partes.join('&')}` : '';
};

export const api = {
  ingresar: (usuario: string, clave: string) => pedir<{ token: string }>('/auth/ingreso', { metodo: 'POST', cuerpo: { usuario, clave } }),
  inicio: () => pedir<Inicio>('/clientes/yo/inicio'),

  /** 03: lo que respondió la persona se guarda apenas lo elige (es dato suyo, no del plan). */
  guardarFrecuencia: (frecuencia: Frecuencia) => pedir<void>('/fecha-cobro/frecuencia', { metodo: 'POST', cuerpo: { frecuencia } }),
  opcionesFecha: (frecuencia: Frecuencia) => pedir<OpcionesFecha>(`/fecha-cobro/opciones?frecuencia=${q(frecuencia)}`),
  confirmarFecha: (frecuencia: Frecuencia, dia: number) => pedir<FechaConfirmada>('/fecha-cobro', { metodo: 'POST', cuerpo: { frecuencia, dia } }),

  creditosApartables: () => pedir<CreditoApartable[]>('/apartado/creditos'),
  opcionesPartes: (credito: string) => pedir<OpcionesPartes>(`/apartado/partes?credito=${q(credito)}`),
  origenApartado: (credito?: string, partes?: number) => pedir<OrigenApartado>(`/apartado/origen${query({ credito, partes })}`),
  activarApartado: (credito: string, partes: number) => pedir<Resumen>('/apartado', { metodo: 'POST', cuerpo: { credito, partes, automatico: true } }),

  ofertaCuenta: () => pedir<OfertaCuenta>('/cuenta-digital/oferta'),
  contratoCuenta: () => pedir<ContratoCuenta>('/cuenta-digital/contrato'),
  firmarCuenta: () => pedir<void>('/cuenta-digital', { metodo: 'POST', cuerpo: { acepta: true } }),

  /** Productos disponibles del inicio que se activan en un toque (p. ej. el depósito a plazo). */
  activarProducto: (id: string) => pedir<Producto>(`/productos/${q(id)}/activar`, { metodo: 'POST' }),

  record: () => pedir<Record>('/clientes/yo/record'),
  avisos: () => pedir<Aviso[]>('/clientes/yo/avisos'),
  avisosLeidos: () => pedir<void>('/clientes/yo/avisos/leidos', { metodo: 'POST' }),
  registrarDispositivo: (token: string, plataforma: string) => pedir<void>('/dispositivos', { metodo: 'POST', cuerpo: { token, plataforma } }),

  /** IA del formulario: pre-resalta la opción que conviene; la persona decide. */
  sugerencias: {
    frecuencia: () => pedir<Sugerencia>('/ai/sugerencias/frecuencia'),
    fecha: (frecuencia: Frecuencia) => pedir<Sugerencia>(`/ai/sugerencias/fecha?frecuencia=${q(frecuencia)}`),
    partes: (credito: string) => pedir<Sugerencia>(`/ai/sugerencias/partes?credito=${q(credito)}`),
  },

  asesor: {
    iniciar: (aviso?: string) => pedir<SesionAsesor>('/asesor/sesiones', { metodo: 'POST', cuerpo: { aviso: aviso ?? null } }),
    enviar: (sesion: string, texto: string) => pedir<RespuestaAsesor>(`/asesor/sesiones/${q(sesion)}/mensajes`, { metodo: 'POST', cuerpo: { texto } }),
    terminar: (sesion: string) => pedir<void>(`/asesor/sesiones/${q(sesion)}/fin`, { metodo: 'POST' }),
  },
};
