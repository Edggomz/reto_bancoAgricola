// Contrato con el backend. Los montos llegan como número; las fechas y textos, ya redactados.

export type Ilustracion = 'calendario' | 'monedas' | 'bolsaDinero' | 'tarjeta' | 'alcancia' | 'check';

export type Inicio = {
  cliente: { nombre: string; iniciales: string };
  /** Nula = no tiene cuenta de ahorro ni de débito con nosotros (se ofrece abrirla). */
  cuenta: { producto: string; saldo: number; numero: string; apartado: number } | null;
  /** Productos bancarios de la persona: tarjetas, créditos personales, hipotecarios y bancarios. */
  creditos: CreditoInicio[];
  /** Crédito con cuota al que se le mueve la fecha de cobro; nulo si no tiene uno. */
  credito: { id: string; cuota: number; diaCobro: number; cambio: { dia: number; desdeMes: string } | null } | null;
  ruta: MiRuta | null;
  record: { meses: number; proximo: string } | null;
  productos: Producto[];
  avisosSinLeer: number;
};

export type CreditoInicio = {
  id: string;
  tipo: 'tarjeta' | 'personal' | 'hipotecario' | 'bancario' | string;
  titulo: string;
  monto: number;
  detalle: string;
  apartable: boolean;
};

export type MiRuta =
  | { estado: 'activa'; hitos: { fecha: string; detalle: string; tipo: 'aparta' | 'paga' }[] }
  | { estado: 'solo-fecha'; dia: number };

export type Producto = {
  id: 'cambiar-fecha' | 'deposito-plazo' | string;
  titulo: string;
  detalle: string;
  ilustracion: Ilustracion;
  /** Lo que se muestra antes de activarlo (solo productos que se activan en un toque). */
  condiciones?: string;
};

export type Frecuencia = 'quincena-fin-de-mes' | 'fin-de-mes' | 'semanal' | 'variable';

export type OpcionesFecha = {
  hoy: number;
  grupos: { titulo: string; dias: DiaOpcion[] }[];
};
export type DiaOpcion = { dia: number; desde: string; nota: string };

export type FechaConfirmada = { dia: number; desde: string; operacion: string };

export type CreditoApartable = { id: string; nombre: string; detalle: string; monto: number; nota: string; ilustracion: Ilustracion };

export type OpcionesPartes = {
  credito: { nombre: string; cuota: number; diaPago: number };
  sugerencia: string;
  opciones: { partes: number; calendario: { fecha: string; detalle: string; monto: number; tipo: 'aparta' | 'paga' }[] }[];
};

/** `cuenta` nula = no tiene cuenta de débito y se abre una (08a). */
export type OrigenApartado = { cuenta: { titulo: string; nombre: string; detalle: string } | null; pasos: string[] };

export type Resumen = { filas: { etiqueta: string; valor: string }[] };

export type OfertaCuenta = { nombre: string; condiciones: string[] };
export type ContratoCuenta = Resumen & { documentos: { titulo: string; url: string }[] };

export type Record = {
  meses: number;
  proximo: string;
  hitos: { n: number; etiqueta: string; hoy: boolean }[];
  explicacion: string;
  suma: { titulo: string; detalle: string }[];
};

export type Aviso = { id: string; titulo: string; cuerpo: string; fecha: string; destino: DestinoAviso | null };
export type DestinoAviso = { vista: 'record' } | { vista: 'asesor'; aviso: string };

export type MensajeAsesor = { id: string; rol: 'asesor' | 'persona'; texto: string };
export type RespuestaAsesor = { mensajes: MensajeAsesor[]; sugerencias: string[] };
export type SesionAsesor = RespuestaAsesor & { sesion: string };

/** Sugerencia de la IA del formulario: la opción que conviene y por qué. La persona sigue decidiendo. */
export type Sugerencia = { opcion: string | null; motivo: string; confianza: number; origen: string };
