import React from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { color, radius, space } from '../theme';
import { IconButton } from './Button';
import { Hueco } from './Dato';
import { Txt } from './Txt';

const logo = require('../../assets/marca/logo.png');

export function AppBar({ left, center, right, padX = space[2] }: { left: React.ReactNode; center: React.ReactNode; right: React.ReactNode; padX?: number }) {
  return (
    <View style={[styles.bar, { paddingHorizontal: padX }]}>
      {left}
      {center}
      {right}
    </View>
  );
}

/** Mantenerlo presionado abre el menú de demostración. */
export function Logo({ onLongPress }: { onLongPress?: () => void }) {
  return (
    <Pressable onLongPress={onLongPress} delayLongPress={600} accessibilityLabel="Bancoagrícola">
      <Image source={logo} style={styles.logo} resizeMode="contain" />
    </Pressable>
  );
}

export function Avatar({ initials, diameter = 32 }: { initials?: string | null; diameter?: number }) {
  return (
    <View style={[styles.avatar, { width: diameter, height: diameter }]}>
      {initials ? (
        <Txt v="labelM" c={color.text.inverse}>
          {initials}
        </Txt>
      ) : (
        <Hueco ancho={14} alto={8} oscuro />
      )}
    </View>
  );
}

export function Title({ children, subtitle }: { children: string; subtitle?: string }) {
  return (
    <View style={styles.title}>
      <Txt v="headingM" align="center" numberOfLines={1}>
        {children}
      </Txt>
      {subtitle ? (
        <Txt v="caption" c={color.text.tertiary} align="center" numberOfLines={1}>
          {subtitle}
        </Txt>
      ) : null}
    </View>
  );
}

export function FlowBar({ title, subtitle, onBack, onClose }: { title: string; subtitle?: string; onBack?: () => void; onClose?: () => void }) {
  return (
    <AppBar
      left={<IconButton glyph="‹" label="Volver" onPress={onBack} />}
      center={<Title subtitle={subtitle}>{title}</Title>}
      right={<IconButton glyph="×" label="Cerrar" onPress={onClose} />}
    />
  );
}

export function HomeBar({ initials, onLongPressLogo, onMenu }: { initials?: string | null; onLongPressLogo?: () => void; onMenu?: () => void }) {
  return (
    <AppBar
      left={
        <View style={styles.slot}>
          <Avatar initials={initials} />
        </View>
      }
      center={<Logo onLongPress={onLongPressLogo} />}
      right={<IconButton glyph="···" label="Más opciones" onPress={onMenu} />}
    />
  );
}

const styles = StyleSheet.create({
  bar: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: color.border.subtle,
    backgroundColor: color.bg.canvas,
  },
  logo: { width: 126, height: 22 },
  slot: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  avatar: { borderRadius: radius.full, backgroundColor: color.bg.avatar, alignItems: 'center', justifyContent: 'center' },
  // ocupa el espacio entre las dos ranuras de 44 pt: queda centrado y no se recorta
  title: { flex: 1, alignItems: 'center' },
});
