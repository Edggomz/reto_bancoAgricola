import { useCallback, useEffect, useRef, useState } from 'react';
import { hayApi } from './cliente';

/** Pide un dato al backend. Sin API configurada, `datos` queda en null y la vista muestra los huecos. */
export function useDatos<T>(pedir: () => Promise<T>, deps: unknown[] = []) {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState(false);
  const [cargando, setCargando] = useState(hayApi());
  const vivo = useRef(true);

  const cargar = useCallback(() => {
    if (!hayApi()) return;
    setCargando(true);
    setError(false);
    pedir()
      .then((d) => vivo.current && setDatos(d))
      .catch(() => vivo.current && setError(true))
      .finally(() => vivo.current && setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    vivo.current = true;
    cargar();
    return () => {
      vivo.current = false;
    };
  }, [cargar]);

  return { datos, error, cargando, recargar: cargar };
}
