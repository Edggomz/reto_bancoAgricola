export function dinero(n: number) {
  const [entero, dec] = Math.abs(n).toFixed(2).split('.');
  return `${n < 0 ? '−' : ''}$${entero.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${dec}`;
}

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export const hora = (d: Date) => `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
export const fechaLarga = (d: Date) => `${DIAS[d.getDay()]} ${d.getDate()} de ${MESES[d.getMonth()]}`;
export const fechaCorta = (d: Date) => `${d.getDate()} ${CORTOS[d.getMonth()]}`;
export const esHoy = (d: Date, ahora = new Date()) => d.toDateString() === ahora.toDateString();
