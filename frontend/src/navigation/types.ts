import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { Frecuencia } from '../api/tipos';

// `entrada: 'fade'` = la vista llega con fundido (× de Figma) en vez de deslizarse
type Entrada = { entrada?: 'fade' };
type Apartado = { credito: string; partes: number };

export type RootStackParamList = {
  Ingreso: undefined;
  Inicio: Entrada | undefined;
  QueDia: undefined;
  EligeFecha: { frecuencia: Frecuencia };
  QueCuota: undefined;
  CuantasPartes: { credito: string };
  Automatico: Apartado;
  // sin datos = se abre la cuenta desde el inicio y se vuelve ahí; con datos = sigue a 08
  AbreCuenta: Apartado | undefined;
  RevisaFirma: Apartado | undefined;
  Avisos: undefined;
  MiRecord: undefined;
  Asesor: { aviso?: string } | undefined;
  Demo: undefined;
};

export type ScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, T>;

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
