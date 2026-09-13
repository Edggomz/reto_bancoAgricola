import React, { useEffect, useRef } from 'react';
import { Animated, BackHandler, Dimensions, Easing, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { color, elevation, radius, space } from '../theme';
import { useBottom } from './Screen';

/** «Ventana» de Figma: velo al 50 % y hoja inferior con asa, encima de la vista que la abre. */
export function Sheet({
  visible,
  children,
  onRequestClose,
  cerrarAlTocarFuera = false,
}: {
  visible: boolean;
  children: React.ReactNode;
  onRequestClose?: () => void;
  cerrarAlTocarFuera?: boolean;
}) {
  const t = useRef(new Animated.Value(0)).current;
  const bottom = useBottom();
  const { top } = useSafeAreaInsets();

  useEffect(() => {
    Animated.timing(t, {
      toValue: visible ? 1 : 0,
      duration: visible ? 320 : 220,
      easing: visible ? Easing.out(Easing.cubic) : Easing.in(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [visible, t]);

  useEffect(() => {
    if (!visible || !onRequestClose) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onRequestClose();
      return true;
    });
    return () => sub.remove();
  }, [visible, onRequestClose]);

  const offset = Dimensions.get('window').height;
  return (
    // sube por encima del margen superior para que el velo cubra la barra de estado
    <View style={[StyleSheet.absoluteFill, { top: -top }]} pointerEvents={visible ? 'auto' : 'none'}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.velo, { opacity: t.interpolate({ inputRange: [0, 1], outputRange: [0, 0.5] }) }]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={cerrarAlTocarFuera ? onRequestClose : undefined} accessible={false} />
      </Animated.View>
      <Animated.View style={[styles.ventana, { paddingBottom: bottom, transform: [{ translateY: t.interpolate({ inputRange: [0, 1], outputRange: [offset, 0] }) }] }]}>
        <View style={styles.asa} />
        {children}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  velo: { backgroundColor: color.bg.scrim },
  ventana: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: space[3],
    paddingHorizontal: space[5],
    gap: space[4],
    alignItems: 'center',
    backgroundColor: color.bg.surface,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    boxShadow: elevation[3],
  },
  asa: { width: 36, height: 4, borderRadius: radius.full, backgroundColor: color.border.default },
});
