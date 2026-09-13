import React from 'react';
import { Image, ImageSourcePropType, Pressable, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import type { Ilustracion as NombreIlustracion } from '../api/tipos';
import { color, elevation, radius, space } from '../theme';
import { Dato, Hueco } from './Dato';
import { Txt } from './Txt';

export function Pasos({ total, hechos }: { total: number; hechos: number }) {
  return (
    <View style={styles.pasos} accessibilityLabel={`Paso ${hechos} de ${total}`}>
      {Array.from({ length: total }, (_, i) => (
        <View key={i} style={[styles.tramo, { backgroundColor: i < hechos ? color.bg.inverse : color.border.subtle }]} />
      ))}
    </View>
  );
}

export function Encabezado({ title, children, kicker }: { title: string; children?: string; kicker?: React.ReactNode }) {
  return (
    <View style={{ gap: space[2] }}>
      {kicker}
      <Txt v="headingXl">{title}</Txt>
      {children ? (
        <Txt v="bodyM" c={color.text.secondary}>
          {children}
        </Txt>
      ) : null}
    </View>
  );
}

export function Card({
  children,
  style,
  sunken = false,
  flat = false,
  stroke = color.border.subtle,
  strokeWidth = 1,
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  sunken?: boolean;
  flat?: boolean;
  stroke?: string;
  strokeWidth?: number;
}) {
  return (
    <View
      style={[
        styles.card,
        sunken ? { backgroundColor: color.bg.surfaceSunken } : { backgroundColor: color.bg.surface, borderWidth: strokeWidth, borderColor: stroke },
        !sunken && !flat && { boxShadow: elevation[1] },
        style,
      ]}
    >
      {children}
    </View>
  );
}

type Tone = 'plan' | 'alDia' | 'sunken';
const tones: Record<Tone, { bg: string; fg: string }> = {
  plan: { bg: color.estado.planBg, fg: color.estado.planFg },
  alDia: { bg: color.estado.alDiaBg, fg: color.estado.alDiaFg },
  sunken: { bg: color.bg.surfaceSunken, fg: color.text.secondary },
};

export function Nota({ tone, children }: { tone: Tone; children: string }) {
  const t = tones[tone];
  return (
    <View style={[styles.nota, { backgroundColor: t.bg }]}>
      <Txt v="bodyS" c={t.fg} style={{ flex: 1 }}>
        {children}
      </Txt>
    </View>
  );
}

export function EstadoBadge({ label = 'Al día' }: { label?: string }) {
  return (
    <View style={styles.badge}>
      <View style={[styles.dot, { width: 7, height: 7, backgroundColor: color.estado.alDiaFg }]} />
      <Txt v="labelM" c={color.estado.alDiaFg}>
        {label}
      </Txt>
    </View>
  );
}

export function Punto({ d = 8, c }: { d?: number; c: string }) {
  return <View style={[styles.dot, { width: d, height: d, backgroundColor: c }]} />;
}

export function Fila({ label, value, ancho = 110 }: { label?: string | null; value?: string | null; ancho?: number }) {
  return (
    <View style={styles.fila}>
      <Dato v="bodyM" c={color.text.secondary} valor={label} ancho={80} />
      <Dato v="labelL" valor={value} ancho={ancho} align="right" tabular={!!value?.includes('$')} style={{ flexShrink: 1 }} />
    </View>
  );
}

export function Divisor() {
  return <View style={{ height: 1, alignSelf: 'stretch', backgroundColor: color.border.subtle }} />;
}

export function Circulo({ d, bg, children, radiusValue = radius.full }: { d: number; bg: string; children: React.ReactNode; radiusValue?: number }) {
  return <View style={[styles.circulo, { width: d, height: d, backgroundColor: bg, borderRadius: radiusValue }]}>{children}</View>;
}

const ilustraciones: Record<NombreIlustracion, ImageSourcePropType> = {
  check: require('../../assets/ilustraciones/check.png'),
  calendario: require('../../assets/ilustraciones/calendario.png'),
  monedas: require('../../assets/ilustraciones/monedas.png'),
  bolsaDinero: require('../../assets/ilustraciones/bolsa-dinero.png'),
  tarjeta: require('../../assets/ilustraciones/tarjeta.png'),
  alcancia: require('../../assets/ilustraciones/alcancia.png'),
};

export function Ilustracion({ name, d }: { name?: NombreIlustracion | null; d: number }) {
  const src = name ? ilustraciones[name] : undefined;
  if (!src) return <Hueco ancho={d} alto={d} style={{ borderRadius: radius.md }} />;
  return <Image source={src} style={{ width: d, height: d }} resizeMode="contain" />;
}

/** Aviso de que los datos no llegaron, con opción de reintentar. */
export function ErrorCarga({ onReintentar, oscuro = false }: { onReintentar: () => void; oscuro?: boolean }) {
  return (
    <Pressable accessibilityRole="button" onPress={onReintentar} style={({ pressed }) => [styles.error, pressed && { opacity: 0.6 }]}>
      <Txt v="bodyS" c={oscuro ? color.text.inverse : color.text.secondary}>
        No pudimos cargar tus datos.{' '}
        <Txt v="labelM" c={oscuro ? color.text.inverse : color.text.link} underline>
          Reintentar
        </Txt>
      </Txt>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pasos: { flexDirection: 'row', gap: space[1] },
  tramo: { flex: 1, height: 4, borderRadius: radius.full },
  card: { borderRadius: radius.md, padding: space[4] },
  nota: { flexDirection: 'row', paddingVertical: space[3], paddingHorizontal: space[4], borderRadius: radius.sm },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 5,
    paddingHorizontal: 11,
    borderRadius: radius.full,
    backgroundColor: color.estado.alDiaBg,
  },
  dot: { borderRadius: radius.full },
  fila: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space[3] },
  circulo: { alignItems: 'center', justifyContent: 'center' },
  error: { minHeight: 44, justifyContent: 'center' },
});
