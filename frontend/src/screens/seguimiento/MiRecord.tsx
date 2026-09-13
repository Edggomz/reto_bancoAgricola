import React from 'react';
import { StyleSheet, View } from 'react-native';
import { AppBar, Body, Card, Dato, ErrorCarga, EstadoBadge, FAB_CLEARANCE, Fab, IconButton, Nota, Punto, Screen, Title, Txt } from '../../components';
import { api } from '../../api/servicios';
import { useDatos } from '../../api/useDatos';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, size, space } from '../../theme';

/** 12: lo que suma y lo que viene. Nada aparece como castigo. */
export function MiRecord({ navigation }: ScreenProps<'MiRecord'>) {
  const { datos, error, recargar } = useDatos(api.record);
  const hitos = datos?.hitos ?? [null, null, null];
  return (
    <Screen>
      <AppBar
        left={<IconButton glyph="‹" label="Volver" onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Inicio'))} />}
        center={<Title>Mi récord</Title>}
        right={<IconButton glyph="···" label="Más opciones" />}
      />
      <Body bottomExtra={FAB_CLEARANCE}>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <Card style={{ gap: space[2] }}>
          <View style={styles.cabecera}>
            <Txt v="overline" c={color.text.tertiary}>
              TU RÉCORD CON NOSOTROS
            </Txt>
            <EstadoBadge />
          </View>
          <View style={styles.cifra}>
            <Dato v="displayXl" valor={datos?.meses} ancho={45} />
            <Txt v="bodyM" c={color.text.secondary} style={{ flex: 1 }}>
              meses seguidos pagando a tiempo
            </Txt>
          </View>
          <View style={styles.viene}>
            <Dato v="labelM" c={color.estado.planFg} valor={datos ? `Tu próximo +1: ${datos.proximo}` : null} ancho={200} />
          </View>
        </Card>

        <View style={{ gap: space[3] }}>
          <Txt v="overline" c={color.text.tertiary}>
            TU CAMINO A 24 DE 24
          </Txt>
          <View style={styles.hitos}>
            {hitos.map((h, i) => (
              <React.Fragment key={i}>
                {i > 0 ? <View style={styles.tramo} /> : null}
                <View style={styles.hito}>
                  <View style={[styles.circulo, h?.hoy ? styles.hoy : styles.futuro]}>
                    {h ? (
                      <Txt v="labelL" c={h.hoy ? color.estado.alDiaFg : color.text.secondary}>
                        {h.n}
                      </Txt>
                    ) : null}
                  </View>
                  <Dato v="caption" c={color.text.secondary} valor={h?.etiqueta} ancho={44} align="center" />
                </View>
              </React.Fragment>
            ))}
          </View>
          <Dato v="bodyS" c={color.text.secondary} valor={datos?.explicacion} ancho="90%" />
        </View>

        <View>
          <Txt v="overline" c={color.text.tertiary}>
            QUÉ TE ESTÁ SUMANDO
          </Txt>
          {(datos?.suma ?? [null, null]).map((s, i, todos) => (
            <View key={i} style={[styles.suma, i < todos.length - 1 && styles.divisor]}>
              <View style={styles.punto}>
                <Punto c={color.estado.alDiaFg} />
              </View>
              <View style={{ flex: 1, gap: space[1] }}>
                <Dato v="labelL" valor={s?.titulo} ancho={220} />
                <Dato v="bodyS" c={color.text.secondary} valor={s?.detalle} ancho={260} />
              </View>
            </View>
          ))}
        </View>

        <Nota tone="sunken">Consulta tu récord las veces que quieras. Sin costo y sin límite.</Nota>
      </Body>
      <Fab onPress={() => navigation.navigate('Asesor')} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  cabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space[3] },
  cifra: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  viene: { flexDirection: 'row', paddingVertical: space[2], paddingHorizontal: space[3], borderRadius: radius.sm, backgroundColor: color.estado.planBg },
  hitos: { flexDirection: 'row' },
  hito: { alignItems: 'center', gap: space[1] },
  tramo: { flex: 1, height: 2, marginTop: 21, backgroundColor: color.border.default },
  circulo: { width: size.controlMd, height: size.controlMd, borderRadius: radius.full, borderWidth: 1.5, alignItems: 'center', justifyContent: 'center' },
  hoy: { backgroundColor: color.estado.alDiaBg, borderColor: color.estado.alDiaFg },
  futuro: { backgroundColor: color.bg.surface, borderColor: color.border.default },
  suma: { flexDirection: 'row', gap: space[3], paddingVertical: space[3] },
  divisor: { borderBottomWidth: 1, borderBottomColor: color.border.subtle },
  punto: { paddingTop: 6 },
});
