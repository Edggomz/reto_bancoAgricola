import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import {
  Body,
  Button,
  Card,
  Circulo,
  Dato,
  Encabezado,
  ErrorCarga,
  Fila,
  FlowBar,
  Footer,
  Ilustracion,
  LinkButton,
  Pasos,
  Screen,
  Sheet,
  Txt,
} from '../../components';
import { api } from '../../api/servicios';
import type { Resumen } from '../../api/tipos';
import { useDatos } from '../../api/useDatos';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, space } from '../../theme';

/** 08, con 09 como ventana. Los pasos los arma el banco con las partes elegidas (con una sola parte, es más corto). */
export function Automatico({ navigation, route }: ScreenProps<'Automatico'>) {
  const cerrar = useCerrarAlInicio();
  const { credito, partes } = route.params;
  const { datos, error, recargar } = useDatos(() => api.origenApartado(credito, partes), [credito, partes]);
  const [enviando, setEnviando] = useState(false);
  const [fallo, setFallo] = useState(false);
  const [lista, setLista] = useState<Resumen | null>(null);

  const activar = async () => {
    if (enviando) return;
    setEnviando(true);
    setFallo(false);
    try {
      setLista(await api.activarApartado(credito, partes));
    } catch {
      setFallo(true);
    } finally {
      setEnviando(false);
    }
  };

  const cuenta = datos?.cuenta;
  return (
    <Screen>
      <FlowBar title="Apartar mi cuota" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Pasos total={3} hechos={3} />
        <Encabezado title="¿Lo hacemos en automático?">
          {partes === 1 ? 'Apartamos tu cuota completa después de tu pago y el día del cobro se paga sola.' : 'Así no tienes que acordarte de nada.'}
        </Encabezado>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <Card flat stroke={color.border.strong} strokeWidth={1.5} style={styles.cuenta}>
          <View style={{ flex: 1, gap: space[1] }}>
            <Dato v="bodyS" c={color.text.secondary} valor={cuenta?.titulo} ancho={160} />
            <Dato v="labelL" valor={cuenta?.nombre} ancho={120} />
            <Dato v="bodyS" c={color.text.secondary} valor={cuenta?.detalle} ancho={200} />
          </View>
          <Circulo d={28} bg={color.bg.inverse}>
            <Txt v="labelM" c={color.text.inverse}>
              ✓
            </Txt>
          </Circulo>
        </Card>
        <Card style={{ gap: space[3] }}>
          <Txt v="overline" c={color.text.tertiary}>
            ASÍ FUNCIONA
          </Txt>
          {(datos?.pasos ?? [null, null, null]).map((p, i) => (
            <View key={i} style={styles.paso}>
              <Circulo d={28} bg={color.bg.surfaceSunken}>
                <Txt v="labelM">{i + 1}</Txt>
              </Circulo>
              <Dato v="bodyM" valor={p} ancho={200} style={{ flex: 1 }} />
            </View>
          ))}
        </Card>
        <Txt v="bodyS" c={color.text.tertiary}>
          Puedes pausarlo cuando quieras desde Mi ruta.
        </Txt>
        {fallo ? <ErrorCarga onReintentar={activar} /> : null}
      </Body>
      <Footer gap={space[2]}>
        <LinkButton label="Ahora no" onPress={() => navigation.navigate('Inicio')} />
        <Button label={enviando ? 'ACTIVANDO…' : 'ACTIVAR EN AUTOMÁTICO'} onPress={activar} />
      </Footer>

      <Sheet visible={!!lista} onRequestClose={() => navigation.navigate('Inicio')}>
        <Ilustracion name="calendario" d={72} />
        <View style={styles.confirmacion}>
          <Txt v="headingXl" align="center">
            Tu ruta quedó lista
          </Txt>
          <Txt v="bodyM" c={color.text.secondary} align="center">
            Esto es lo que acordamos. Te avisamos cada vez que pase algo.
          </Txt>
        </View>
        <Card sunken style={styles.resumen}>
          {lista?.filas.map((f) => <Fila key={f.etiqueta} label={f.etiqueta} value={f.valor} ancho={170} />)}
        </Card>
        <Button label="IR AL INICIO" onPress={() => navigation.navigate('Inicio')} />
      </Sheet>
    </Screen>
  );
}

const styles = StyleSheet.create({
  cuenta: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  paso: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  confirmacion: { alignSelf: 'stretch', alignItems: 'center', gap: space[1] },
  resumen: { alignSelf: 'stretch', gap: space[3] },
});
