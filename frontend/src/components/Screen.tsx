import React, { createContext, useContext, useEffect, useState } from 'react';
import { Dimensions, Keyboard, Platform, ScrollView, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { color, size, space } from '../theme';

// escala del marco de 390 pt en pantallas angostas (ver App.tsx)
export const DesignScaleContext = createContext(1);

/** Alto del teclado en pt de diseño; 0 si está cerrado. */
export function useKeyboardHeight() {
  const scale = useContext(DesignScaleContext);
  const [height, setHeight] = useState(0);
  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', (e) =>
      // en Android de borde a borde se mide desde el teclado hasta el fondo de la pantalla
      setHeight(Platform.OS === 'android' ? Dimensions.get('screen').height - e.endCoordinates.screenY : e.endCoordinates.height),
    );
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', () => setHeight(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);
  return height / scale;
}

/** Margen inferior: nunca menos que el de Figma (34 pt). */
export function useBottom(min: number = size.safeBottom) {
  return Math.max(useSafeAreaInsets().bottom, min);
}

export function Screen({ children, bg = color.bg.canvas, light = false }: { children: React.ReactNode; bg?: string; light?: boolean }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.screen, { backgroundColor: bg, paddingTop: insets.top }]}>
      <StatusBar style={light ? 'light' : 'dark'} />
      {children}
    </View>
  );
}

/** «Contenido» de Figma: 20 · 20 · 0 · 20, con scroll si no cabe. */
export function Body({
  children,
  gap = space[5],
  pad = [space[5], space[5], 0, space[5]],
  bottomExtra = 0,
  style,
}: {
  children: React.ReactNode;
  gap?: number;
  pad?: [number, number, number, number];
  bottomExtra?: number;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <ScrollView
      style={styles.body}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={[{ gap, paddingTop: pad[0], paddingRight: pad[1], paddingBottom: pad[2] + bottomExtra, paddingLeft: pad[3] }, style]}
    >
      {children}
    </ScrollView>
  );
}

/** «Pie» de Figma: botones fijos abajo; con el teclado abierto suben sobre él. */
export function Footer({ children, gap = space[3], bottom }: { children: React.ReactNode; gap?: number; bottom?: number }) {
  const b = useBottom(bottom);
  const teclado = useKeyboardHeight();
  return <View style={[styles.footer, { gap, paddingBottom: teclado > 0 ? teclado + space[3] : b }]}>{children}</View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  body: { flex: 1 },
  footer: { paddingTop: space[4], paddingHorizontal: space[5] },
});
