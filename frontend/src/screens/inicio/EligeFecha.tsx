import React, { useState } from 'react';
import { Animated, StyleSheet, View } from 'react-native';
import {
  Body,
  Button,
  Card,
  Chip,
  Dato,
  Divisor,
  Encabezado,
  ErrorCarga,
  Fila,
  FlowBar,
  Footer,
  Hueco,
  Ilustracion,
  Nota,
  Pasos,
  Screen,
  Sheet,
  Txt,
  useSacudida,
} from '../../components';
import { api } from '../../api/servicios';
import type { FechaConfirmada } from '../../api/tipos';
import { useDatos } from '../../api/useDatos';
import { dinero } from '../../formato';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, size, space } from '../../theme';

/**
 * 04 y 05: los días salen del backend según cuándo le pagan; la IA sugiere uno y la persona elige.
 * Si ese día corre la cuota, el interés se ve en 04 antes de confirmar y queda escrito en 05.
 */
export function EligeFecha({ navigation, route }: ScreenProps<'EligeFecha'>) {
  const cerrar = useCerrarAlInicio();
  const { frecuencia } = route.params;
  const { datos, error, recargar } = useDatos(() => api.opcionesFecha(frecuencia), [frecuencia]);
  const { datos: sugerencia } = useDatos(() => api.sugerencias.fecha(frecuencia), [frecuencia]);
  const [dia, setDia] = useState<number | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [fallo, setFallo] = useState(false);
  const [listo, setListo] = useState<FechaConfirmada | null>(null);
  const { estilo, sacudir } = useSacudida();

  const opcion = datos?.grupos.flatMap((g) => g.dias).find((d) => d.dia === dia) ?? null;
  const sugerido = sugerencia?.opcion ? Number(sugerencia.opcion) : null;
  const conInteres = (opcion?.interes ?? 0) > 0;

  const confirmar = async () => {
    if (dia === null || enviando) return;
    setEnviando(true);
    setFallo(false);
    try {
      setListo(await api.confirmarFecha(frecuencia, dia, conInteres));
    } catch {
      setFallo(true);
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Screen>
      <FlowBar title="Cambiar fecha de cobro" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Pasos total={2} hechos={2} />
        <Encabezado title="¿Qué día te queda mejor?">Solo te mostramos días en los que ya te pagaron.</Encabezado>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <Animated.View style={[{ gap: space[5] }, estilo]}>
          {datos
            ? datos.grupos.map((g) => (
                <View key={g.titulo} style={{ gap: space[2] }}>
                  <Txt v="overline" c={color.text.tertiary}>
                    {g.titulo}
                  </Txt>
                  <View style={styles.dias}>
                    {g.dias.map((d) => (
                      <Chip key={d.dia} label={d.dia === sugerido ? `Día ${d.dia} · Sugerido` : `Día ${d.dia}`} selected={d.dia === dia} onPress={() => setDia(d.dia)} />
                    ))}
                  </View>
                </View>
              ))
            : [0, 1].map((i) => (
                <View key={i} style={{ gap: space[2] }}>
                  <Dato v="overline" valor={null} ancho={200} />
                  <View style={styles.dias}>
                    <Hueco ancho={77} alto={size.controlMd} style={styles.chipVacio} />
                    <Hueco ancho={77} alto={size.controlMd} style={styles.chipVacio} />
                  </View>
                </View>
              ))}
        </Animated.View>
        {sugerencia?.opcion && sugerencia.motivo ? <Nota tone="plan">{sugerencia.motivo}</Nota> : null}
        <Card style={{ gap: space[3] }}>
          <View style={styles.cambio}>
            <View style={{ gap: space[1] }}>
              <Txt v="bodyS" c={color.text.tertiary}>
                Hoy
              </Txt>
              <Dato v="headingL" valor={datos ? `Día ${datos.hoy}` : null} ancho={60} />
            </View>
            <Txt v="headingL" c={color.text.tertiary}>
              →
            </Txt>
            <View style={{ gap: space[1], alignItems: 'flex-end' }}>
              <Txt v="bodyS" c={color.text.tertiary} align="right">
                {opcion ? opcion.desde : 'Elige un día'}
              </Txt>
              <Txt v="headingL" c={opcion ? color.estado.alDiaFg : color.text.tertiary} align="right">
                {opcion ? `Día ${opcion.dia}` : 'Día —'}
              </Txt>
            </View>
          </View>
          <Divisor />
          <Txt v="bodyS" c={color.text.secondary}>
            {opcion ? opcion.nota : 'Al elegir, te mostramos desde cuándo aplica y si lleva interés.'}
          </Txt>
          {opcion ? (
            <Txt v="labelL" c={conInteres ? color.text.primary : color.estado.alDiaFg} tabular>
              {opcion.costo}
            </Txt>
          ) : null}
        </Card>
        <Nota tone="sunken">Tu cuota y tu plazo no cambian. Si tu cobro se corre unos días, esos días llevan interés una sola vez, y lo ves aquí antes de confirmar.</Nota>
        {fallo ? <ErrorCarga onReintentar={confirmar} /> : null}
      </Body>
      <Footer>
        <Button
          label={enviando ? 'CONFIRMANDO…' : dia ? `${conInteres ? 'ACEPTAR Y CONFIRMAR' : 'CONFIRMAR'} EL DÍA ${dia}` : 'CONFIRMAR'}
          variant={dia ? 'primario' : 'inactivo'}
          onPressInactivo={sacudir}
          onPress={confirmar}
        />
      </Footer>

      <Sheet visible={!!listo} onRequestClose={() => navigation.navigate('Inicio')}>
        <Ilustracion name="check" d={72} />
        <View style={styles.confirmacion}>
          <Txt v="headingXl" align="center">
            {`Listo.\nTu cobro es el día ${listo?.dia ?? ''}`}
          </Txt>
          <Txt v="bodyS" c={color.text.tertiary} align="center">
            {listo ? `Desde el ${listo.desde} · Operación N.º ${listo.operacion}` : ''}
          </Txt>
        </View>
        {listo ? (
          <Card style={styles.interes}>
            <Fila label="Interés de este cambio" value={dinero(listo.interes)} />
            <Txt v="bodyS" c={color.text.secondary}>
              {listo.costo}
            </Txt>
          </Card>
        ) : null}
        <Card sunken style={styles.recomendacion}>
          <View style={styles.etiqueta}>
            <Txt v="overline" c={color.text.inverse}>
              RECOMENDADO
            </Txt>
          </View>
          <Txt v="headingM">Aparta tu cuota en partes</Txt>
          <Txt v="bodyM" c={color.text.secondary}>
            De cada pago que recibes guardamos una parte. El {listo?.dia} tu cuota ya está completa y se paga sola.
          </Txt>
        </Card>
        <View style={styles.acciones}>
          <Button label="APARTAR MI CUOTA" onPress={() => navigation.navigate('QueCuota')} />
          <Button label="AHORA NO" variant="secundario" onPress={() => navigation.navigate('Inicio')} />
        </View>
      </Sheet>
    </Screen>
  );
}

const styles = StyleSheet.create({
  dias: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  chipVacio: { borderRadius: radius.full },
  cambio: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space[3] },
  confirmacion: { alignSelf: 'stretch', alignItems: 'center', gap: space[1] },
  interes: { alignSelf: 'stretch', gap: space[2] },
  recomendacion: { alignSelf: 'stretch', gap: space[2] },
  etiqueta: { alignSelf: 'flex-start', paddingVertical: space[1], paddingHorizontal: space[2], borderRadius: radius.full, backgroundColor: color.bg.inverse },
  acciones: { alignSelf: 'stretch', gap: space[3] },
});
