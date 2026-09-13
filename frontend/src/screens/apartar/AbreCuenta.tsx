import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Body, Button, Card, Circulo, Dato, ErrorCarga, FlowBar, Footer, Ilustracion, LinkButton, Pasos, Screen, Txt } from '../../components';
import { api } from '../../api/servicios';
import { useDatos } from '../../api/useDatos';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, space } from '../../theme';

/** 08a: sin cuenta con nosotros, la abre ahí mismo. Llega desde 07 (sigue a 08) o desde el inicio (vuelve ahí). */
export function AbreCuenta({ navigation, route }: ScreenProps<'AbreCuenta'>) {
  const cerrar = useCerrarAlInicio();
  const enFlujo = !!route.params;
  const { datos, error, recargar } = useDatos(api.ofertaCuenta);
  return (
    <Screen>
      <FlowBar title={enFlujo ? 'Apartar mi cuota' : 'Abrir mi cuenta'} onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        {enFlujo ? <Pasos total={3} hechos={3} /> : null}
        <View style={{ gap: space[2] }}>
          <Ilustracion name="alcancia" d={88} />
          <Txt v="headingXl">{enFlujo ? 'Para apartar, abramos tu cuenta' : 'Abramos tu cuenta'}</Txt>
          <Txt v="bodyM" c={color.text.secondary}>
            Ya eres cliente, así que no te pedimos papeles. Toma menos de dos minutos.
          </Txt>
        </View>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <Card style={{ gap: space[3] }}>
          <Dato v="headingM" valor={datos?.nombre} ancho={200} />
          {(datos?.condiciones ?? [null, null, null]).map((c, i) => (
            <View key={i} style={styles.condicion}>
              <Circulo d={24} bg={color.estado.alDiaBg}>
                <Txt v="labelM" c={color.estado.alDiaFg}>
                  ✓
                </Txt>
              </Circulo>
              <Dato v="bodyM" valor={c} ancho={220} style={{ flex: 1 }} />
            </View>
          ))}
        </Card>
      </Body>
      <Footer gap={space[2]}>
        <LinkButton label="Ahora no" onPress={() => navigation.navigate('Inicio')} />
        <Button label="ABRIR MI CUENTA" onPress={() => navigation.navigate('RevisaFirma', route.params)} />
      </Footer>
    </Screen>
  );
}

const styles = StyleSheet.create({
  condicion: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
});
