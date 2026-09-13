const BASE = (process.env.EXPO_PUBLIC_API_URL ?? '').replace(/\/+$/, '');

let token: string | null = null;

export const hayApi = () => BASE.length > 0;
export const guardarToken = (t: string | null) => {
  token = t;
};

export class ErrorApi extends Error {
  constructor(public estado: number, mensaje: string) {
    super(mensaje);
  }
}

export async function pedir<T>(ruta: string, opciones: { metodo?: 'GET' | 'POST'; cuerpo?: unknown } = {}): Promise<T> {
  if (!hayApi()) throw new ErrorApi(0, 'Sin EXPO_PUBLIC_API_URL');
  const res = await fetch(BASE + ruta, {
    method: opciones.metodo ?? 'GET',
    headers: {
      Accept: 'application/json',
      ...(opciones.cuerpo !== undefined ? { 'Content-Type': 'application/json' } : null),
      ...(token ? { Authorization: `Bearer ${token}` } : null),
    },
    body: opciones.cuerpo !== undefined ? JSON.stringify(opciones.cuerpo) : undefined,
  });
  if (!res.ok) throw new ErrorApi(res.status, await res.text());
  // 204 o cuerpo vacío
  const texto = await res.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}
