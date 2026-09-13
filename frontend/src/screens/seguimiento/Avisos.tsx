import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { Body, ErrorCarga, IconButton, Screen, Txt } from '../../components';
import { api } from '../../api/servicios';
import { useDatos } from '../../api/useDatos';
import { agregarAvisos, useAvisos } from '../../avisos/almacen';
import { AvisoVacio, GrupoAvisos, Reloj, TarjetaAviso } from '../../avisos/componentes';
import { abrirDestino } from '../../avisos/push';
import { hayApi } from '../../api/cliente';
import { esHoy } from '../../formato';
import type { ScreenProps } from '../../navigation/types';
import { color, space } from '../../theme';

/** 11: los avisos que llegaron por push, con el aspecto de la pantalla bloqueada. Al verlos quedan leídos. */
export function Avisos({ navigation }: ScreenProps<'Avisos'>) {
  const { datos, error, cargando, recargar } = useDatos(api.avisos);
  useEffect(() => {
    if (datos) {
      agregarAvisos(datos);
      api.avisosLeidos().catch(() => {});
    }
  }, [datos]);
  const avisos = useAvisos();
  const hoy = avisos.filter((a) => esHoy(new Date(a.fecha)));
  const antes = avisos.filter((a) => !esHoy(new Date(a.fecha)));
  const esperando = !hayApi() || (cargando && avisos.length === 0);

  return (
    <Screen bg={color.bg.inverse} light>
      <View style={styles.capa}>
        <View style={styles.atras}>
          <IconButton glyph="‹" label="Volver" c={color.text.inverse} onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Inicio'))} />
        </View>
      </View>
      <Body gap={space[3]} pad={[space[6], space[3], space[6], space[3]]}>
        <Reloj />
        {error ? <ErrorCarga onReintentar={recargar} oscuro /> : null}
        {esperando ? (
          <GrupoAvisos titulo="HOY">
            <AvisoVacio />
            <AvisoVacio />
          </GrupoAvisos>
        ) : avisos.length === 0 ? (
          <Txt v="bodyS" c={color.text.inverse} align="center">
            Aún no tienes avisos.
          </Txt>
        ) : (
          <>
            {hoy.length ? (
              <GrupoAvisos titulo="HOY">
                {hoy.map((a) => (
                  <TarjetaAviso key={a.id} aviso={a} onPress={a.destino ? () => abrirDestino(a.destino) : undefined} />
                ))}
              </GrupoAvisos>
            ) : null}
            {antes.length ? (
              <GrupoAvisos titulo="ANTES">
                {antes.map((a) => (
                  <TarjetaAviso key={a.id} aviso={a} onPress={a.destino ? () => abrirDestino(a.destino) : undefined} />
                ))}
              </GrupoAvisos>
            ) : null}
          </>
        )}
      </Body>
    </Screen>
  );
}

const styles = StyleSheet.create({
  // ‹ flota arriba a la izquierda, como en Figma, sin mover el reloj
  capa: { height: 0, zIndex: 1 },
  atras: { position: 'absolute', left: space[2], top: 3.5 },
});
