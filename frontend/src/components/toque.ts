// Evita que dos toques seguidos (o dos botones a la vez) disparen dos navegaciones.
let ultimo = 0;

export function unToque(fn?: () => void, espera = 450) {
  return () => {
    const ahora = Date.now();
    if (!fn || ahora - ultimo < espera) return;
    ultimo = ahora;
    fn();
  };
}
