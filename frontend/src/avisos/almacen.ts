import { useSyncExternalStore } from 'react';
import type { Aviso } from '../api/tipos';

// avisos recibidos: los que trae el backend y los que llegan por push mientras la app está abierta
let avisos: Aviso[] = [];
const oyentes = new Set<() => void>();

export function agregarAvisos(nuevos: Aviso[]) {
  const porId = new Map(avisos.map((a) => [a.id, a]));
  for (const a of nuevos) porId.set(a.id, a);
  avisos = [...porId.values()].sort((a, b) => b.fecha.localeCompare(a.fecha));
  oyentes.forEach((o) => o());
}

export function useAvisos() {
  return useSyncExternalStore(
    (o) => {
      oyentes.add(o);
      return () => oyentes.delete(o);
    },
    () => avisos,
  );
}
