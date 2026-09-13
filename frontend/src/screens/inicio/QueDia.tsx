import React, { useState } from 'react';
import { Animated, View } from 'react-native';
import { Body, Button, Chip, Encabezado, FlowBar, Footer, Nota, Pasos, Screen, useSacudida } from '../../components';
import { api } from '../../api/servicios';
import type { Frecuencia } from '../../api/tipos';
import { useDatos } from '../../api/useDatos';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { space } from '../../theme';

const OPCIONES: { id: Frecuencia; label: string }[] = [
  { id: 'quincena-fin-de-mes', label: 'Quincena y fin de mes' },
  { id: 'fin-de-mes', label: 'Solo fin de mes' },
  { id: 'semanal', label: 'Cada semana' },
  { id: 'variable', label: 'Es variable' },
];

/** 03: la respuesta se manda al banco apenas se elige; la IA sugiere según cómo entran sus abonos. */
export function QueDia({ navigation }: ScreenProps<'QueDia'>) {
  const cerrar = useCerrarAlInicio();
  const [elegida, setElegida] = useState<Frecuencia | null>(null);
  const { estilo, sacudir } = useSacudida();
  const { datos: sugerencia } = useDatos(api.sugerencias.frecuencia);
  const sugerida = OPCIONES.find((o) => o.id === sugerencia?.opcion);

  const continuar = () => {
    if (!elegida) return;
    api.guardarFrecuencia(elegida).catch(() => {});
    navigation.navigate('EligeFecha', { frecuencia: elegida });
  };

  return (
    <Screen>
      <FlowBar title="Cambiar fecha de cobro" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Pasos total={2} hechos={1} />
        <Encabezado title="¿Qué día te pagan?">Así movemos tu cobro para que caiga cuando ya tienes el dinero, no antes.</Encabezado>
        <Animated.View style={[{ gap: space[3] }, estilo]}>
          {OPCIONES.map((o) => (
            <Chip key={o.id} label={o.id === sugerida?.id ? `${o.label} · Sugerido` : o.label} fill selected={o.id === elegida} onPress={() => setElegida(o.id)} />
          ))}
        </Animated.View>
        {sugerida && sugerencia ? <Nota tone="plan">{sugerencia.motivo}</Nota> : null}
        <Nota tone="sunken">Si es variable, usamos el promedio de lo que te llegó en los últimos tres meses y te avisamos antes de cobrar.</Nota>
      </Body>
      <Footer>
        <Button label="CONTINUAR" variant={elegida ? 'primario' : 'inactivo'} onPressInactivo={sacudir} onPress={continuar} />
      </Footer>
    </Screen>
  );
}
