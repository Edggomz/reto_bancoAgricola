import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import type { Aviso } from '../api/tipos';
import { Hueco, Txt } from '../components';
import { esHoy, fechaCorta, fechaLarga, hora } from '../formato';
import { color, radius, space } from '../theme';

/** Fecha y hora del teléfono, como en la pantalla bloqueada. */
export function Reloj() {
  const [ahora, setAhora] = useState(new Date());
  useEffect(() => {
    const t = setInterval(() => setAhora(new Date()), 15_000);
    return () => clearInterval(t);
  }, []);
  return (
    <View style={styles.reloj}>
      <Txt v="labelL" c={color.text.inverse}>
        {fechaLarga(ahora)}
      </Txt>
      <Txt v="numeralXl" c={color.text.inverse}>
        {hora(ahora)}
      </Txt>
    </View>
  );
}

export function TarjetaAviso({ aviso, onPress }: { aviso: Aviso; onPress?: () => void }) {
  const f = new Date(aviso.fecha);
  return (
    <Pressable
      disabled={!onPress}
      onPress={onPress}
      accessibilityRole={onPress ? 'button' : undefined}
      style={({ pressed }) => [styles.aviso, pressed && { opacity: 0.85, transform: [{ scale: 0.99 }] }]}
    >
      <Cabecera derecha={esHoy(f) ? hora(f) : fechaCorta(f)} />
      <Txt v="labelL">{aviso.titulo}</Txt>
      <Txt v="bodyS" c={color.text.secondary}>
        {aviso.cuerpo}
      </Txt>
    </Pressable>
  );
}

/** Hueco con la forma de un aviso mientras no llegan. */
export function AvisoVacio() {
  return (
    <View style={styles.aviso}>
      <Cabecera />
      <Hueco ancho="60%" alto={12} style={{ marginVertical: 4 }} />
      <Hueco ancho="90%" alto={10} style={{ marginVertical: 4 }} />
      <Hueco ancho="70%" alto={10} style={{ marginVertical: 4 }} />
    </View>
  );
}

function Cabecera({ derecha }: { derecha?: string }) {
  return (
    <View style={styles.cabecera}>
      <View style={styles.app}>
        <View style={styles.icono} />
        <Txt v="overline" c={color.text.tertiary}>
          BANCOAGRÍCOLA · MI RUTA
        </Txt>
      </View>
      {derecha ? (
        <Txt v="caption" c={color.text.tertiary}>
          {derecha}
        </Txt>
      ) : null}
    </View>
  );
}

export function GrupoAvisos({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <>
      <Txt v="overline" c={color.text.inverse}>
        {titulo}
      </Txt>
      {children}
    </>
  );
}

const styles = StyleSheet.create({
  reloj: { alignItems: 'center' },
  aviso: { gap: space[1], paddingVertical: space[3], paddingHorizontal: space[4], borderRadius: radius.lg, backgroundColor: color.bg.surface },
  cabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space[2] },
  app: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  icono: { width: 20, height: 20, borderRadius: radius.xs, backgroundColor: color.bg.brand },
});
