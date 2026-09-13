import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';
import * as Device from 'expo-device';
import { api } from '../api/servicios';
import { hayApi } from '../api/cliente';
import type { Aviso, DestinoAviso } from '../api/tipos';
import { navegacion } from '../navigation/acciones';
import { agregarAvisos } from './almacen';

/**
 * Carga que manda el backend en cada push (campo `data`):
 *   { id: string, fecha: ISO-8601, destino: { vista: 'record' } | { vista: 'asesor', aviso: id } | null }
 * El título y el cuerpo van en la notificación misma.
 */
type CargaPush = { id?: string; fecha?: string; destino?: DestinoAviso | null };

export function abrirDestino(destino: DestinoAviso | null | undefined) {
  if (!destino || !navegacion.isReady()) return;
  if (destino.vista === 'record') navegacion.navigate('MiRecord');
  if (destino.vista === 'asesor') navegacion.navigate('Asesor', { aviso: destino.aviso });
}

// Expo Go no recibe push remotos en Android desde el SDK 53: hace falta una build de desarrollo
const enExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;

export async function iniciarPush() {
  if (Platform.OS === 'web' || enExpoGo || !Device.isDevice) return () => {};
  const Notifications = await import('expo-notifications');

  Notifications.setNotificationHandler({
    handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: false, shouldSetBadge: false }),
  });

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('mi-ruta', { name: 'Mi ruta', importance: Notifications.AndroidImportance.DEFAULT });
  }

  const permiso = await Notifications.getPermissionsAsync();
  const final = permiso.granted ? permiso : await Notifications.requestPermissionsAsync();
  const projectId = Constants.expoConfig?.extra?.eas?.projectId as string | undefined;
  if (final.granted && projectId && hayApi()) {
    const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId });
    api.registrarDispositivo(token, Platform.OS).catch(() => {});
  }

  const aAviso = (n: import('expo-notifications').Notification): Aviso => {
    const carga = (n.request.content.data ?? {}) as CargaPush;
    return {
      id: carga.id ?? n.request.identifier,
      titulo: n.request.content.title ?? '',
      cuerpo: n.request.content.body ?? '',
      fecha: carga.fecha ?? new Date(n.date).toISOString(),
      destino: carga.destino ?? null,
    };
  };

  const recibido = Notifications.addNotificationReceivedListener((n) => agregarAvisos([aAviso(n)]));
  const tocado = Notifications.addNotificationResponseReceivedListener((r) => {
    const aviso = aAviso(r.notification);
    agregarAvisos([aviso]);
    abrirDestino(aviso.destino);
  });
  // si la app se abrió desde un aviso
  const ultimo = await Notifications.getLastNotificationResponseAsync();
  if (ultimo) abrirDestino(aAviso(ultimo.notification).destino);

  return () => {
    recibido.remove();
    tocado.remove();
  };
}
