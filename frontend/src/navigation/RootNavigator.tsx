import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { LinkingOptions } from '@react-navigation/native';
import * as Linking from 'expo-linking';
import type { RootStackParamList } from './types';
import { color } from '../theme';
import { Ingreso } from '../screens/inicio/Ingreso';
import { Inicio } from '../screens/inicio/Inicio';
import { QueDia } from '../screens/inicio/QueDia';
import { EligeFecha } from '../screens/inicio/EligeFecha';
import { QueCuota } from '../screens/apartar/QueCuota';
import { CuantasPartes } from '../screens/apartar/CuantasPartes';
import { Automatico } from '../screens/apartar/Automatico';
import { AbreCuenta } from '../screens/apartar/AbreCuenta';
import { RevisaFirma } from '../screens/apartar/RevisaFirma';
import { Avisos } from '../screens/seguimiento/Avisos';
import { MiRecord } from '../screens/seguimiento/MiRecord';
import { Asesor } from '../screens/asesor/Asesor';
import { Demo } from '../screens/Demo';

const Stack = createNativeStackNavigator<RootStackParamList>();

// cada vista tiene su dirección: web, enlaces profundos y avisos push
export const linking: LinkingOptions<RootStackParamList> = {
  prefixes: [Linking.createURL('/')],
  config: {
    screens: {
      Ingreso: '',
      Inicio: 'inicio',
      QueDia: 'que-dia',
      EligeFecha: 'elige-fecha',
      QueCuota: 'que-cuota',
      CuantasPartes: 'cuantas-partes',
      Automatico: { path: 'automatico', parse: { partes: Number } },
      AbreCuenta: { path: 'abre-cuenta', parse: { partes: Number } },
      RevisaFirma: { path: 'revisa-firma', parse: { partes: Number } },
      Avisos: 'avisos',
      MiRecord: 'mi-record',
      Asesor: 'asesor',
      Demo: 'demo',
    },
  },
};

// NAVIGATE de Figma = deslizar desde la derecha (300 ms); × y el asesor = fundido (250 ms)
export function RootNavigator() {
  return (
    <Stack.Navigator
      initialRouteName="Ingreso"
      screenOptions={({ route }) => {
        const fade = (route.params as { entrada?: string } | undefined)?.entrada === 'fade';
        return {
          headerShown: false,
          animation: fade ? 'fade' : 'slide_from_right',
          animationDuration: fade ? 250 : 300,
          contentStyle: { backgroundColor: color.bg.canvas },
        };
      }}
    >
      <Stack.Screen name="Ingreso" component={Ingreso} />
      <Stack.Screen name="Inicio" component={Inicio} />
      <Stack.Screen name="QueDia" component={QueDia} />
      <Stack.Screen name="EligeFecha" component={EligeFecha} />
      <Stack.Screen name="QueCuota" component={QueCuota} />
      <Stack.Screen name="CuantasPartes" component={CuantasPartes} />
      <Stack.Screen name="Automatico" component={Automatico} />
      <Stack.Screen name="AbreCuenta" component={AbreCuenta} />
      <Stack.Screen name="RevisaFirma" component={RevisaFirma} />
      <Stack.Screen name="Avisos" component={Avisos} />
      <Stack.Screen name="MiRecord" component={MiRecord} />
      <Stack.Screen name="Asesor" component={Asesor} options={{ animation: 'fade', animationDuration: 250 }} />
      <Stack.Screen name="Demo" component={Demo} options={{ animation: 'fade', animationDuration: 250 }} />
    </Stack.Navigator>
  );
}
