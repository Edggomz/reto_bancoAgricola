import React, { useRef } from 'react';
import { Animated, Easing, Pressable, StyleProp, StyleSheet, ViewStyle } from 'react-native';
import { color, radius, size } from '../theme';
import { unToque } from './toque';
import { Txt } from './Txt';

type Variant = 'primario' | 'secundario' | 'inactivo';

const fill: Record<Variant, { bg: string; fg: string; border?: string }> = {
  primario: { bg: color.action.primaryBg, fg: color.action.primaryFg },
  secundario: { bg: color.action.secondaryBg, fg: color.action.secondaryFg, border: color.action.secondaryBorder },
  inactivo: { bg: color.action.disabledBg, fg: color.action.disabledFg },
};

/** Sacudida corta para avisar que falta algo antes de seguir. */
export function useSacudida() {
  const x = useRef(new Animated.Value(0)).current;
  const sacudir = () => {
    const paso = (v: number) => Animated.timing(x, { toValue: v, duration: 55, easing: Easing.linear, useNativeDriver: true });
    Animated.sequence([paso(-6), paso(6), paso(-4), paso(4), paso(0)]).start();
  };
  return { estilo: { transform: [{ translateX: x }] }, sacudir };
}

export function Button({
  label,
  onPress,
  onPressInactivo,
  variant = 'primario',
  height = size.controlLg,
  style,
}: {
  label: string;
  onPress?: () => void;
  /** Se ve inactivo pero responde: sacude y avisa qué falta. */
  onPressInactivo?: () => void;
  variant?: Variant;
  height?: number;
  style?: StyleProp<ViewStyle>;
}) {
  const f = fill[variant];
  const { estilo, sacudir } = useSacudida();
  const inactivo = variant === 'inactivo';
  return (
    <Animated.View style={[styles.stretch, estilo, style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: inactivo }}
        onPress={inactivo ? () => (sacudir(), onPressInactivo?.()) : unToque(onPress)}
        style={({ pressed }) => [
          styles.base,
          { height, backgroundColor: f.bg },
          f.border && { borderWidth: 1.5, borderColor: f.border },
          pressed && variant === 'primario' && { backgroundColor: color.action.primaryBgPressed },
          pressed && variant === 'secundario' && { backgroundColor: color.bg.surfaceSunken },
        ]}
      >
        <Txt v="overline" c={f.fg}>
          {label}
        </Txt>
      </Pressable>
    </Animated.View>
  );
}

/** «ACTIVAR» de los productos: blanco en reposo, se pinta de amarillo al tocarlo y vuelve. */
export function FlashButton({ label, onPress, style }: { label: string; onPress?: () => void; style?: StyleProp<ViewStyle> }) {
  const t = useRef(new Animated.Value(0)).current;
  const to = (v: number, duration: number, delay = 0) =>
    Animated.timing(t, { toValue: v, duration, delay, easing: Easing.out(Easing.cubic), useNativeDriver: false });
  const ir = unToque(onPress);
  return (
    <Pressable
      accessibilityRole="button"
      onPressIn={() => to(1, 120).start()}
      onPressOut={() => to(0, 300, 450).start()}
      onPress={() => setTimeout(ir, 160)}
      style={style}
    >
      <Animated.View
        style={[
          styles.base,
          {
            height: size.controlMd,
            borderWidth: 1.5,
            backgroundColor: t.interpolate({ inputRange: [0, 1], outputRange: [color.action.secondaryBg, color.action.primaryBg] }),
            borderColor: t.interpolate({ inputRange: [0, 1], outputRange: [color.action.secondaryBorder, color.action.primaryBg] }),
          },
        ]}
      >
        <Txt v="overline" c={color.action.secondaryFg}>
          {label}
        </Txt>
      </Animated.View>
    </Pressable>
  );
}

export function LinkButton({ label, onPress, style }: { label: string; onPress?: () => void; style?: StyleProp<ViewStyle> }) {
  return (
    <Pressable accessibilityRole="link" onPress={unToque(onPress)} style={({ pressed }) => [styles.link, pressed && { opacity: 0.5 }, style]}>
      <Txt v="labelL" c={color.text.link} underline>
        {label}
      </Txt>
    </Pressable>
  );
}

/** Área de 44 × 44 con un glifo (‹ × ···). */
export function IconButton({ glyph, onPress, c = color.text.secondary, label }: { glyph: string; onPress?: () => void; c?: string; label?: string }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label ?? glyph}
      onPress={unToque(onPress)}
      hitSlop={4}
      style={({ pressed }) => [styles.icon, pressed && { opacity: 0.4 }]}
    >
      <Txt v="headingL" c={c}>
        {glyph}
      </Txt>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  stretch: { alignSelf: 'stretch' },
  base: { alignSelf: 'stretch', borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  link: { height: size.controlMd, alignItems: 'center', justifyContent: 'center' },
  icon: { width: size.controlMd, height: size.controlMd, alignItems: 'center', justifyContent: 'center' },
});
