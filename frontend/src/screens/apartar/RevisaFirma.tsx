import React, { useState } from 'react';
import { Animated, Linking, Pressable, StyleSheet, View } from 'react-native';
import { Body, Button, Card, Dato, Divisor, Encabezado, ErrorCarga, Fila, FlowBar, Footer, LinkButton, Screen, Txt, useSacudida } from '../../components';
import { api } from '../../api/servicios';
import { useDatos } from '../../api/useDatos';
import { useCerrarAlInicio } from '../../navigation/acciones';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, size, space } from '../../theme';

/** 08b: sin la casilla marcada no se firma. Al firmar sigue a 08 (si venía apartando) o vuelve al inicio. */
export function RevisaFirma({ navigation, route }: ScreenProps<'RevisaFirma'>) {
  const cerrar = useCerrarAlInicio();
  const { datos, error, recargar } = useDatos(api.contratoCuenta);
  const [acepto, setAcepto] = useState(false);
  const [firmando, setFirmando] = useState(false);
  const [fallo, setFallo] = useState(false);
  const { estilo, sacudir } = useSacudida();

  const firmar = async () => {
    if (firmando) return;
    setFirmando(true);
    setFallo(false);
    try {
      await api.firmarCuenta();
      if (route.params) navigation.navigate('Automatico', route.params);
      else navigation.navigate('Inicio', { entrada: 'fade' });
    } catch {
      setFallo(true);
    } finally {
      setFirmando(false);
    }
  };

  // la última fila del resumen va separada, como en Figma
  const filas = datos?.filas ?? [null, null, null, null, null].map(() => ({ etiqueta: null, valor: null }));
  return (
    <Screen>
      <FlowBar title="Abrir mi cuenta" onBack={navigation.goBack} onClose={cerrar} />
      <Body>
        <Encabezado title="Revisa y firma">Esto es lo que vas a firmar. Te enviamos una copia a tu correo.</Encabezado>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        <Card style={{ gap: space[3] }}>
          {filas.map((f, i) => (
            <React.Fragment key={i}>
              {i === filas.length - 1 ? <Divisor /> : null}
              <Fila label={f.etiqueta} value={f.valor} />
            </React.Fragment>
          ))}
        </Card>
        <View>
          {(datos?.documentos ?? [null, null]).map((d, i, todos) => (
            <View key={i} style={[styles.documento, i < todos.length - 1 && styles.divisor]}>
              <Dato v="bodyM" valor={d?.titulo} ancho={200} style={{ flex: 1 }} />
              <LinkButton label="Ver" style={{ width: size.controlMd }} onPress={d ? () => Linking.openURL(d.url) : undefined} />
            </View>
          ))}
        </View>
        <Animated.View style={estilo}>
          <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: acepto }} onPress={() => setAcepto(!acepto)} style={styles.acepto}>
            <View style={[styles.casilla, acepto ? styles.marcada : styles.vacia]}>
              {acepto ? (
                <Txt v="labelM" c={color.text.inverse}>
                  ✓
                </Txt>
              ) : null}
            </View>
            <Txt v="bodyM" style={{ flex: 1 }}>
              Leí y acepto el contrato y la autorización
            </Txt>
          </Pressable>
        </Animated.View>
        {fallo ? <ErrorCarga onReintentar={firmar} /> : null}
      </Body>
      <Footer>
        <Button
          label={firmando ? 'FIRMANDO…' : 'FIRMAR CON FACE ID'}
          variant={acepto ? 'primario' : 'inactivo'}
          onPressInactivo={sacudir}
          onPress={firmar}
        />
      </Footer>
    </Screen>
  );
}

const styles = StyleSheet.create({
  documento: { height: 52, flexDirection: 'row', alignItems: 'center', gap: space[3] },
  divisor: { borderBottomWidth: 1, borderBottomColor: color.border.subtle },
  acepto: { height: size.controlMd, flexDirection: 'row', alignItems: 'center', gap: space[3] },
  casilla: { width: 24, height: 24, borderRadius: radius.xs, alignItems: 'center', justifyContent: 'center' },
  marcada: { backgroundColor: color.bg.inverse },
  vacia: { borderWidth: 1.5, borderColor: color.border.strong, backgroundColor: color.bg.surface },
});
