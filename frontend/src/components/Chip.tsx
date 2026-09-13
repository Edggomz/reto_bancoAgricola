import React, { useEffect, useRef } from 'react';
import { Animated, Easing, Pressable, StyleProp, StyleSheet, ViewStyle } from 'react-native';
import { color, radius, size, space } from '../theme';
import { Txt } from './Txt';

const BORDE = 1.5;

/** «Chip» de Figma: Normal · Elegido. Se encoge al tocarlo y se pinta al elegirlo. */
export function Chip({
  label,
  selected = false,
  onPress,
  fill = false,
  style,
}: {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  fill?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const elegido = useRef(new Animated.Value(selected ? 1 : 0)).current;
  const presion = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(elegido, { toValue: selected ? 1 : 0, duration: 160, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
  }, [selected, elegido]);

  const presionar = (v: number) => Animated.timing(presion, { toValue: v, duration: v ? 80 : 160, useNativeDriver: false }).start();

  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      onPressIn={() => presionar(1)}
      onPressOut={() => presionar(0)}
      style={[fill ? styles.fill : styles.hug, style]}
    >
      <Animated.View
        style={[
          styles.base,
          {
            backgroundColor: elegido.interpolate({ inputRange: [0, 1], outputRange: [color.action.secondaryBg, color.bg.brand] }),
            borderColor: elegido.interpolate({ inputRange: [0, 1], outputRange: [color.border.default, color.border.strong] }),
            transform: [{ scale: presion.interpolate({ inputRange: [0, 1], outputRange: [1, 0.96] }) }],
            opacity: presion.interpolate({ inputRange: [0, 1], outputRange: [1, 0.85] }),
          },
        ]}
      >
        <Txt v="labelL" c={selected ? color.text.onBrand : color.text.secondary}>
          {label}
        </Txt>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  hug: { alignSelf: 'flex-start' },
  fill: { alignSelf: 'stretch' },
  // en Figma el trazo no suma al layout; aquí sí, por eso se descuenta del padding
  base: {
    height: size.controlMd,
    paddingHorizontal: space[4] - BORDE,
    borderWidth: BORDE,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
