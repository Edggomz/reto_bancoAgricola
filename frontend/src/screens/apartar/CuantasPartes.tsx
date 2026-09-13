import React, { useState } from 'react';
import { Animated, StyleSheet, View } from 'react-native';
import {
  Body,
  Button,
  Card,
  Chip,
  Dato,
  Encabezado,
  ErrorCarga,
  FlowBar,
  Footer,
  Hueco,
  Nota,
  Pasos,
  Punto,
  Screen,
  Txt,
  useSacudida,
} from '../../components';
import { api } from '../../api/servicios';
import { useDatos } from '../../api/useDatos';
import { dinero } from '../../formato';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, size, space } from '../../theme';

const etiqueta = (partes: number) => (partes === 1 ? '1 parte' : `${partes} partes`);

/** 07: las partes que se ofrecen dependen de cuántas veces le pagan (lo decide el banco). */
export function CuantasPartes({ navigation, route }: ScreenProps<'CuantasPartes'>) {
  const cerrar = useCerrarAlInicio();
  const { credito } = route.params;
  const { datos, error, recargar } = useDatos(() => api.opcionesPartes(credito), [credito]);
  const { datos: sugerencia } = useDatos(() => api.sugerencias.partes(credito), [credito]);
  const [partes, setPartes] = useState<number | null>(null);
  const [yendo, setYendo] = useState(false);
  const [fallo, setFallo] = useState(false);
  const { estilo, sacudir } = useSacudida();

  const opcion = datos?.opciones.find((o) => o.partes === partes) ?? null;
  const sugerido = sugerencia?.opcion ? Number(sugerencia.opcion) : null;

  // el banco decide si aparta de su cuenta (08) o si primero abre una (08a)
  const continuar = async () => {
    if (!partes || yendo) return;
    setYendo(true);
    setFallo(false);
    try {
      const origen = await api.origenApartado(credito, partes);
      navigation.navigate(origen.cuenta ? 'Automatico' : 'AbreCuenta', { credito, partes });
    } catch {
      setFallo(true);
    } finally {
      setYendo(false);
    }
  };

  return (
    <Screen>
      <FlowBar title="Apartar mi cuota" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Pasos total={3} hechos={2} />
        <Encabezado
          kicker={<Dato v="labelM" c={color.text.secondary} valor={datos ? `${datos.credito.nombre} · cuota de ${dinero(datos.credito.cuota)}` : null} ancho={220} />}
          title="¿En cuántas partes la apartamos?"
        />
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <View style={{ gap: space[2] }}>
          <Animated.View style={[styles.chips, estilo]}>
            {datos
              ? datos.opciones.map((o) => (
                  <Chip key={o.partes} label={o.partes === sugerido ? `${etiqueta(o.partes)} · Sugerido` : etiqueta(o.partes)} selected={o.partes === partes} onPress={() => setPartes(o.partes)} />
                ))
              : [0, 1, 2].map((i) => <Hueco key={i} ancho={91} alto={size.controlMd} style={{ borderRadius: radius.full }} />)}
          </Animated.View>
          <Dato v="bodyS" c={color.text.tertiary} valor={datos?.sugerencia} ancho={260} />
        </View>
        {sugerencia?.opcion && sugerencia.motivo ? <Nota tone="plan">{sugerencia.motivo}</Nota> : null}
        <Card style={styles.calendario}>
          {opcion ? (
            opcion.calendario.map((f, i) => (
              <View key={f.fecha + f.tipo} style={[styles.fila, i < opcion.calendario.length - 1 && styles.divisor]}>
                <Punto d={10} c={f.tipo === 'paga' ? color.estado.alDiaFg : color.estado.planFg} />
                <View style={{ flex: 1 }}>
                  <Txt v="labelL">{f.fecha}</Txt>
                  <Txt v="bodyS" c={color.text.secondary}>
                    {f.detalle}
                  </Txt>
                </View>
                <Txt v={f.tipo === 'paga' ? 'numeralM' : 'labelL'} align="right" tabular>
                  {dinero(f.monto)}
                </Txt>
              </View>
            ))
          ) : (
            <View style={styles.sinElegir}>
              <Txt v="bodyS" c={color.text.secondary}>
                Elige en cuántas partes y te mostramos las fechas.
              </Txt>
            </View>
          )}
        </Card>
        <Nota tone="sunken">
          {`Apartar no es pagar antes. El dinero se congela en tu cuenta, sigue siendo tuyo y ${datos ? `el día ${datos.credito.diaPago}` : 'el día del cobro'} se paga completo.`}
        </Nota>
        {fallo ? <ErrorCarga onReintentar={continuar} /> : null}
      </Body>
      <Footer>
        <Button label={yendo ? 'UN MOMENTO…' : 'CONTINUAR'} variant={partes ? 'primario' : 'inactivo'} onPressInactivo={sacudir} onPress={continuar} />
      </Footer>
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  calendario: { paddingVertical: space[2], paddingHorizontal: space[4] },
  fila: { flexDirection: 'row', alignItems: 'center', gap: space[3], paddingVertical: space[3] },
  divisor: { borderBottomWidth: 1, borderBottomColor: color.border.subtle },
  sinElegir: { paddingVertical: space[3] },
});
