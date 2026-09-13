import type { MensajeAsesor } from '../../api/tipos';

export type Mensaje = MensajeAsesor & { fallo?: boolean };

export type Conversacion = {
  sesion: string | null;
  aviso: string | null;
  inicio: Date;
  mensajes: Mensaje[];
  sugerencias: string[];
};

// la conversación sobrevive a «‹»: solo se borra cuando la persona la termina
let actual: Conversacion | null = null;

export const conversacionActual = () => actual;
export const guardarConversacion = (c: Conversacion | null) => {
  actual = c;
};
export const nuevaConversacion = (aviso: string | null): Conversacion => ({ sesion: null, aviso, inicio: new Date(), mensajes: [], sugerencias: [] });
