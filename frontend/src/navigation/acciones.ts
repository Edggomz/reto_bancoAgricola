import { useEffect } from 'react';
import { CommonActions, createNavigationContainerRef, useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from './types';

type Nav = NativeStackNavigationProp<RootStackParamList>;

// para navegar desde fuera de las vistas (avisos push)
export const navegacion = createNavigationContainerRef<RootStackParamList>();

/** × de Figma: cancela el flujo y vuelve al inicio con fundido. */
export function useCerrarAlInicio() {
  const navigation = useNavigation<Nav>();
  return () => {
    const { routes } = navigation.getState();
    if (routes.some((r) => r.name === 'Inicio')) {
      navigation.setOptions({ animation: 'fade' });
      // el cambio de animación tiene que llegar antes de sacar la vista
      setTimeout(() => navigation.popTo('Inicio'), 16);
    } else {
      navigation.navigate('Inicio', { entrada: 'fade' });
    }
  };
}

/** El inicio es raíz: al terminar de entrar borra lo que quedó debajo, así «atrás» sale de la app. */
export function useRaiz() {
  const navigation = useNavigation<Nav>();
  const route = useRoute();
  useEffect(() => {
    return navigation.addListener('transitionEnd', (e) => {
      if (e.data.closing) return;
      const state = navigation.getState();
      const top = state.routes[state.index];
      if (top.key !== route.key || state.routes.length <= 1) return;
      navigation.dispatch(CommonActions.reset({ ...state, routes: [top], index: 0 }));
    });
  }, [navigation, route.key]);
}
