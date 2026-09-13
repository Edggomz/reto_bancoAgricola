import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { CommonActions } from '@react-navigation/native';
import { AppBar, Body, IconButton, Screen, Title, Txt } from '../components';
import type { RootStackParamList, ScreenProps } from '../navigation/types';
import { guardarConversacion } from './asesor/sesion';
import { color, radius, space } from '../theme';

type Destino = { label: string; name: keyof RootStackParamList };

// solo vistas que no necesitan datos de la anterior; las demás se alcanzan siguiendo el flujo
const DESTINOS: Destino[] = [
  { label: '01 · Ingreso', name: 'Ingreso' },
  { label: '02 · Inicio', name: 'Inicio' },
  { label: '03 · ¿Qué día te pagan?', name: 'QueDia' },
  { label: '06 · ¿Qué cuota apartamos?', name: 'QueCuota' },
  { label: '11 · Avisos', name: 'Avisos' },
  { label: '12 · Mi récord', name: 'MiRecord' },
  { label: '13 · Asesor bancario', name: 'Asesor' },
];

/** Menú de demostración (fuera de Figma): se abre manteniendo presionado el logo. */
export function Demo({ navigation }: ScreenProps<'Demo'>) {
  // cada demostración empieza con el asesor sin conversación
  useEffect(() => guardarConversacion(null), []);
  const abrir = (d: Destino) => navigation.dispatch(CommonActions.reset({ index: 0, routes: [{ name: d.name }] }));
  return (
    <Screen>
      <AppBar left={<IconButton glyph="‹" label="Volver" onPress={navigation.goBack} />} center={<Title>Demostración</Title>} right={<View style={{ width: 44 }} />} />
      <Body gap={space[3]} pad={[space[5], space[5], space[8], space[5]]}>
        <Txt v="overline" c={color.text.tertiary}>
          IR A UNA VISTA
        </Txt>
        {DESTINOS.map((d) => (
          <Pressable key={d.label} onPress={() => abrir(d)} style={({ pressed }) => [styles.opcion, pressed && { backgroundColor: color.bg.surfaceSunken }]}>
            <Txt v="labelL">{d.label}</Txt>
            <Txt v="headingL" c={color.text.tertiary}>
              ›
            </Txt>
          </Pressable>
        ))}
      </Body>
    </Screen>
  );
}

const styles = StyleSheet.create({
  opcion: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[4],
    borderWidth: 1,
    borderColor: color.border.subtle,
    borderRadius: radius.md,
    backgroundColor: color.bg.surface,
  },
});
