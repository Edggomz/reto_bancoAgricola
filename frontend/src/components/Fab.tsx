import React from 'react';
import { Pressable, StyleSheet } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { color, elevation, radius, size, space } from '../theme';
import { useBottom } from './Screen';
import { unToque } from './toque';
import { Txt } from './Txt';

// ícono de conversación, tal como sale de Figma
export function ChatIcon() {
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24" fill="none">
      <Path d="M2 7C2 4.79086 3.79086 3 6 3H18C20.2091 3 22 4.79086 22 7V14C22 16.2091 20.2091 18 18 18H6C3.79086 18 2 16.2091 2 14V7Z" fill={color.text.inverse} />
      <Path d="M5 17H12L5 23V17Z" fill={color.text.inverse} />
      <Circle cx={7.5} cy={10.5} r={1.5} fill={color.bg.inverse} />
      <Circle cx={12.5} cy={10.5} r={1.5} fill={color.bg.inverse} />
      <Circle cx={17.5} cy={10.5} r={1.5} fill={color.bg.inverse} />
    </Svg>
  );
}

/** Botón flotante del asesor: 16 pt sobre el margen inferior. */
export function Fab({ onPress }: { onPress: () => void }) {
  const bottom = useBottom() + space[4];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Asesor bancario"
      onPress={unToque(onPress)}
      style={({ pressed }) => [styles.fab, { bottom }, pressed && { opacity: 0.85, transform: [{ scale: 0.97 }] }]}
    >
      <ChatIcon />
      <Txt v="labelL" c={color.text.inverse}>
        Asesor
      </Txt>
    </Pressable>
  );
}

// aire al final del scroll para que el botón flotante no tape contenido
export const FAB_CLEARANCE = size.controlLg + space[4];

const styles = StyleSheet.create({
  fab: {
    position: 'absolute',
    right: space[4],
    height: size.controlLg,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    paddingLeft: space[4],
    paddingRight: space[5],
    borderRadius: radius.full,
    backgroundColor: color.bg.inverse,
    boxShadow: elevation[2],
  },
});
