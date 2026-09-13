import React from 'react';
import { StyleProp, View, ViewStyle } from 'react-native';
import { color, radius, text, TextVariant } from '../theme';
import { Txt } from './Txt';

/** Espacio reservado para un dato que todavía no llega del backend. */
export function Hueco({ ancho, alto, oscuro = false, style }: { ancho: number | `${number}%`; alto: number; oscuro?: boolean; style?: StyleProp<ViewStyle> }) {
  return (
    <View
      accessibilityLabel="Cargando"
      style={[{ width: ancho, height: alto, borderRadius: radius.xs, backgroundColor: oscuro ? 'rgba(255, 255, 255, 0.16)' : color.border.subtle }, style]}
    />
  );
}

/** Texto que viene del backend; mientras no llega, un hueco del alto de la línea. */
export function Dato({
  v,
  c,
  valor,
  ancho,
  oscuro,
  align,
  tabular,
  style,
}: {
  v: TextVariant;
  c?: string;
  valor: string | number | null | undefined;
  ancho: number | `${number}%`;
  oscuro?: boolean;
  align?: 'left' | 'right' | 'center';
  tabular?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  if (valor === null || valor === undefined || valor === '') {
    const t = text[v];
    const alignSelf = align === 'right' ? 'flex-end' : align === 'center' ? 'center' : undefined;
    return (
      <View style={[{ height: t.lineHeight, justifyContent: 'center', alignSelf }, style]}>
        <Hueco ancho={ancho} alto={Math.round(t.fontSize * 0.75)} oscuro={oscuro} />
      </View>
    );
  }
  return (
    <Txt v={v} c={c} align={align} tabular={tabular} style={style as never}>
      {valor}
    </Txt>
  );
}
