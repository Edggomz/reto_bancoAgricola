import React, { useState } from 'react';
import { Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { SafeAreaInsetsContext, SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { NavigationContainer, NavigationState } from '@react-navigation/native';
import { useFonts } from 'expo-font';
import { OpenSans_300Light, OpenSans_400Regular, OpenSans_600SemiBold, OpenSans_700Bold } from '@expo-google-fonts/open-sans';
import { RootNavigator, linking } from './src/navigation/RootNavigator';
import { navegacion } from './src/navigation/acciones';
import { iniciarPush } from './src/avisos/push';
import { DesignScaleContext } from './src/components';
import { color, size } from './src/theme';

export default function App() {
  const [fontsReady] = useFonts({ OpenSans_300Light, OpenSans_400Regular, OpenSans_600SemiBold, OpenSans_700Bold });
  if (!fontsReady) return null;

  const app = (
    <NavigationContainer ref={navegacion} linking={linking} onReady={() => iniciarPush().catch(() => {})} onStateChange={registrarRuta}>
      <RootNavigator />
    </NavigationContainer>
  );

  return <SafeAreaProvider>{Platform.OS === 'web' ? <WebPhone>{app}</WebPhone> : <FitToDesign>{app}</FitToDesign>}</SafeAreaProvider>;
}

// solo en desarrollo: deja la vista actual y la pila en el log, para las pruebas automáticas
function registrarRuta(state: NavigationState | undefined) {
  if (!__DEV__ || !state) return;
  console.log(`[ruta] ${state.routes.map((r) => r.name).join(' > ')}`);
}

/**
 * En teléfonos más angostos que el marco de Figma (el S23 Ultra mide 384 dp) se diagrama
 * a 390 pt y se reduce en proporción, para que los cortes de línea sean los del diseño.
 */
function FitToDesign({ children }: { children: React.ReactNode }) {
  const window = useWindowDimensions();
  const [area, setArea] = useState({ width: window.width, height: window.height });
  const insets = useSafeAreaInsets();
  const s = Math.min(1, area.width / size.frameMobileW);
  const scaled = { top: insets.top / s, bottom: insets.bottom / s, left: insets.left / s, right: insets.right / s };
  return (
    <View style={{ flex: 1, backgroundColor: color.bg.canvas }} onLayout={(e) => setArea(e.nativeEvent.layout)}>
      {s === 1 ? (
        children
      ) : (
        <View style={{ width: size.frameMobileW, height: area.height / s, transformOrigin: 'top left', transform: [{ scale: s }] }}>
          <DesignScaleContext.Provider value={s}>
            <SafeAreaInsetsContext.Provider value={scaled}>{children}</SafeAreaInsetsContext.Provider>
          </DesignScaleContext.Provider>
        </View>
      )}
    </View>
  );
}

// en web se dibuja un teléfono de 390 × 844 con la barra de estado de 54 pt de Figma
const WEB_INSETS = { top: 54, bottom: 0, left: 0, right: 0 };

function WebPhone({ children }: { children: React.ReactNode }) {
  const { width, height } = useWindowDimensions();
  return (
    <View style={styles.webRoot}>
      <View style={{ width: Math.min(width, size.frameMobileW), height: Math.min(height, size.frameMobileH), overflow: 'hidden' }}>
        <SafeAreaInsetsContext.Provider value={WEB_INSETS}>{children}</SafeAreaInsetsContext.Provider>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  webRoot: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: color.border.subtle },
});
