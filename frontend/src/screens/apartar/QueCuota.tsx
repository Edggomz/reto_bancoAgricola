import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Body, Dato, Encabezado, ErrorCarga, FlowBar, Ilustracion, Pasos, Screen, Txt, unToque } from '../../components';
import { api } from '../../api/servicios';
import type { CreditoApartable } from '../../api/tipos';
import { useDatos } from '../../api/useDatos';
import { dinero } from '../../formato';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, space } from '../../theme';

/** 06: solo aparece con dos o más créditos; con uno, pasa directo a 07. */
export function QueCuota({ navigation }: ScreenProps<'QueCuota'>) {
  const cerrar = useCerrarAlInicio();
  const { datos, error, recargar } = useDatos(api.creditosApartables);

  useEffect(() => {
    if (datos?.length === 1) navigation.replace('CuantasPartes', { credito: datos[0].id });
  }, [datos, navigation]);

  return (
    <Screen>
      <FlowBar title="Apartar mi cuota" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Pasos total={3} hechos={1} />
        <Encabezado title="¿Qué cuota quieres apartar?">Tienes dos créditos. Elige uno; después puedes sumar el otro.</Encabezado>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <View style={{ gap: space[3] }}>
          {datos
            ? datos.map((c) => <Opcion key={c.id} credito={c} onPress={() => navigation.navigate('CuantasPartes', { credito: c.id })} />)
            : [0, 1].map((i) => <Opcion key={i} />)}
        </View>
      </Body>
    </Screen>
  );
}

function Opcion({ credito, onPress }: { credito?: CreditoApartable; onPress?: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={!credito}
      onPress={unToque(onPress)}
      style={({ pressed }) => [styles.opcion, pressed && { backgroundColor: color.bg.surfaceSunken, transform: [{ scale: 0.99 }] }]}
    >
      <Ilustracion name={credito?.ilustracion} d={40} />
      <View style={styles.texto}>
        <Dato v="labelL" valor={credito?.nombre} ancho={120} />
        <Dato v="bodyS" c={color.text.secondary} valor={credito?.detalle} ancho={70} />
      </View>
      <View style={styles.monto}>
        <Dato v="numeralM" valor={credito ? dinero(credito.monto) : null} ancho={75} align="right" tabular />
        <Dato v="caption" c={color.text.tertiary} valor={credito?.nota} ancho={80} align="right" />
      </View>
      <Txt v="headingL" c={color.text.tertiary}>
        ›
      </Txt>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  opcion: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    padding: space[4],
    borderWidth: 1.5,
    borderColor: color.border.default,
    borderRadius: radius.md,
    backgroundColor: color.bg.surface,
  },
  texto: { flex: 1, gap: space[1] },
  monto: { alignItems: 'flex-end', gap: space[1] },
});
