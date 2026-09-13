import React, { useCallback, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import {
  Body,
  Button,
  Card,
  Dato,
  ErrorCarga,
  EstadoBadge,
  FAB_CLEARANCE,
  Fab,
  FlashButton,
  HomeBar,
  Hueco,
  Ilustracion,
  Punto,
  Screen,
  Sheet,
  Txt,
  unToque,
} from '../../components';
import { guardarToken } from '../../api/cliente';
import { api } from '../../api/servicios';
import type { CreditoInicio, Inicio as DatosInicio, MiRuta, Producto as DatosProducto } from '../../api/tipos';
import { useDatos } from '../../api/useDatos';
import { guardarConversacion } from '../asesor/sesion';
import { dinero } from '../../formato';
import { useRaiz } from '../../navigation/acciones';
import { color, elevation, radius, size, space } from '../../theme';

/** 02, 10 y 10b: el mismo inicio; todo lo que se ve lo decide el backend según lo que la persona tiene. */
export function Inicio() {
  const navigation = useNavigation();
  useRaiz();
  const { datos, error, recargar } = useDatos(api.inicio);
  const [menu, setMenu] = useState(false);
  const [activando, setActivando] = useState<DatosProducto | null>(null);
  const [enviando, setEnviando] = useState(false);
  // al volver de un flujo, el estado de la ruta pudo cambiar
  const primera = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (primera.current) primera.current = false;
      else recargar();
    }, [recargar]),
  );

  const d = datos as DatosInicio | null;

  const activar = (p: DatosProducto) => {
    if (p.id === 'cambiar-fecha') navigation.navigate('QueDia');
    else setActivando(p);
  };

  // los productos que se activan en un toque desaparecen del inicio al confirmarse
  const confirmarProducto = async () => {
    if (!activando || enviando) return;
    setEnviando(true);
    try {
      await api.activarProducto(activando.id);
      setActivando(null);
      recargar();
    } catch {
      // se queda la ventana abierta para reintentar
    } finally {
      setEnviando(false);
    }
  };

  const salir = () => {
    setMenu(false);
    guardarToken(null);
    guardarConversacion(null);
    navigation.navigate('Ingreso');
  };

  return (
    <Screen>
      <HomeBar initials={d?.cliente.iniciales} onLongPressLogo={() => navigation.navigate('Demo')} onMenu={() => setMenu(true)} />
      <Body gap={space[3]} bottomExtra={FAB_CLEARANCE}>
        {error ? <ErrorCarga onReintentar={recargar} /> : null}
        {d ? <Txt v="headingXl">Hola, {d.cliente.nombre}</Txt> : <Dato v="headingXl" valor={null} ancho={150} />}

        {d && !d.cuenta ? (
          <SinCuenta onAbrir={() => navigation.navigate('AbreCuenta', undefined)} />
        ) : (
          <View style={styles.saldo}>
            <Dato v="labelM" c={color.text.inverse} valor={d?.cuenta?.producto} ancho={220} oscuro />
            <Dato v="numeralXl" c={color.text.inverse} valor={d?.cuenta ? dinero(d.cuenta.saldo) : null} ancho={170} oscuro tabular />
            <Dato v="caption" c={color.text.inverse} valor={d?.cuenta ? `Saldo disponible · N.º ${d.cuenta.numero}` : null} ancho={190} oscuro />
            {d?.cuenta && d.cuenta.apartado > 0 ? (
              <Txt v="caption" c={color.text.inverse} tabular>
                Apartado para tu cuota · {dinero(d.cuenta.apartado)}
              </Txt>
            ) : null}
          </View>
        )}

        <View style={styles.otras}>
          {d ? (
            d.creditos.length ? (
              d.creditos.map((c) => <Credito key={c.id} credito={c} />)
            ) : (
              <Card style={[styles.cuenta, styles.cuentaAncha]}>
                <Txt v="labelM" c={color.text.secondary}>
                  Sin créditos activos
                </Txt>
                <Txt v="caption" c={color.text.tertiary}>
                  Cuando tengas uno, aquí lo ves.
                </Txt>
              </Card>
            )
          ) : (
            [0, 1].map((i) => <Credito key={i} />)
          )}
        </View>

        {d?.ruta ? <TarjetaRuta ruta={d.ruta} onApartar={() => navigation.navigate('QueCuota')} /> : null}

        {d?.record ? (
          <Pressable accessibilityRole="button" onPress={unToque(() => navigation.navigate('MiRecord'))} style={({ pressed }) => [pressed && { opacity: 0.85 }]}>
            <Card style={styles.record}>
              <View style={{ flex: 1, gap: space[1] }}>
                <View style={styles.rutaCabecera}>
                  <Txt v="overline" c={color.text.tertiary}>
                    TU RÉCORD
                  </Txt>
                  <EstadoBadge />
                </View>
                <Txt v="headingM">
                  {d.record.meses} {d.record.meses === 1 ? 'mes seguido' : 'meses seguidos'} pagando a tiempo
                </Txt>
                <Txt v="caption" c={color.text.secondary}>
                  Tu próximo +1: {d.record.proximo}
                </Txt>
              </View>
              <Txt v="headingL" c={color.text.tertiary}>
                ›
              </Txt>
            </Card>
          </Pressable>
        ) : null}

        {!d || d.productos.length ? (
          <View style={{ gap: space[3] }}>
            <Txt v="overline" c={color.text.tertiary}>
              TIENES PRODUCTOS DISPONIBLES
            </Txt>
            {d
              ? d.productos.map((p) => <Producto key={p.id} producto={p} onActivar={() => activar(p)} />)
              : [0, 1].map((i) => <ProductoVacio key={i} />)}
          </View>
        ) : null}
      </Body>
      <Fab onPress={() => navigation.navigate('Asesor')} />

      <Sheet visible={menu} onRequestClose={() => setMenu(false)} cerrarAlTocarFuera>
        <View style={styles.menu}>
          <Opcion
            label="Avisos"
            detalle={d && d.avisosSinLeer > 0 ? `${d.avisosSinLeer} sin leer` : 'Lo que ya pasó, confirmado'}
            onPress={() => {
              setMenu(false);
              navigation.navigate('Avisos');
            }}
          />
          <Opcion
            label="Mi récord"
            detalle="Lo que suma y lo que viene"
            onPress={() => {
              setMenu(false);
              navigation.navigate('MiRecord');
            }}
          />
          <Opcion label="Cerrar sesión" detalle="Vuelves al ingreso" onPress={salir} />
        </View>
      </Sheet>

      <Sheet visible={!!activando} onRequestClose={() => setActivando(null)} cerrarAlTocarFuera>
        <Ilustracion name={activando?.ilustracion ?? 'monedas'} d={72} />
        <View style={styles.confirmacion}>
          <Txt v="headingXl" align="center">
            {activando?.titulo ?? ''}
          </Txt>
          <Txt v="bodyM" c={color.text.secondary} align="center">
            {activando?.detalle ?? ''}
          </Txt>
        </View>
        {activando?.condiciones ? (
          <Card sunken style={styles.condiciones}>
            <Txt v="bodyS" c={color.text.secondary}>
              {activando.condiciones}
            </Txt>
          </Card>
        ) : null}
        <View style={styles.acciones}>
          <Button label={enviando ? 'ACTIVANDO…' : 'ACTIVAR'} onPress={confirmarProducto} />
          <Button label="AHORA NO" variant="secundario" onPress={() => setActivando(null)} />
        </View>
      </Sheet>
    </Screen>
  );
}

/** Sin cuenta con nosotros: se dice claro y se ofrece abrirla (puerta de quien solo tiene crédito). */
function SinCuenta({ onAbrir }: { onAbrir: () => void }) {
  return (
    <View style={styles.saldo}>
      <Txt v="labelM" c={color.text.inverse}>
        Cuenta de ahorro
      </Txt>
      <Txt v="headingL" c={color.text.inverse}>
        Aún no tienes una cuenta con nosotros
      </Txt>
      <Txt v="caption" c={color.text.inverse}>
        Ábrela en dos minutos, sin costo y sin saldo mínimo. De ahí se aparta y se paga tu cuota.
      </Txt>
      <Button label="ABRIR MI CUENTA" variant="secundario" height={size.controlMd} onPress={onAbrir} style={{ marginTop: space[2] }} />
    </View>
  );
}

const TITULOS: Record<string, string> = { tarjeta: 'Tarjeta de crédito', personal: 'Crédito personal', hipotecario: 'Crédito hipotecario', bancario: 'Crédito bancario' };

function Credito({ credito }: { credito?: CreditoInicio }) {
  return (
    <Card style={styles.cuenta}>
      <Txt v="labelM" c={color.text.secondary} numberOfLines={1}>
        {credito ? TITULOS[credito.tipo] ?? credito.titulo : ' '}
      </Txt>
      <Dato v="numeralM" valor={credito ? dinero(credito.monto) : null} ancho={90} tabular />
      <Dato v="caption" c={color.text.tertiary} valor={credito?.detalle} ancho={110} />
    </Card>
  );
}

function Producto({ producto, onActivar }: { producto: DatosProducto; onActivar: () => void }) {
  return (
    <Card style={styles.producto}>
      <Ilustracion name={producto.ilustracion} d={44} />
      <View style={styles.productoTexto}>
        <Txt v="labelL">{producto.titulo}</Txt>
        <Txt v="bodyS" c={color.text.secondary}>
          {producto.detalle}
        </Txt>
      </View>
      <FlashButton label="ACTIVAR" onPress={onActivar} style={styles.activar} />
    </Card>
  );
}

function ProductoVacio() {
  return (
    <Card style={styles.producto}>
      <Ilustracion d={44} />
      <View style={styles.productoTexto}>
        <Dato v="labelL" valor={null} ancho={120} />
        <Dato v="bodyS" valor={null} ancho={90} />
      </View>
      <Hueco ancho={104} alto={size.controlMd} style={{ borderRadius: radius.full }} />
    </Card>
  );
}

function Opcion({ label, detalle, onPress }: { label: string; detalle: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={unToque(onPress)} style={({ pressed }) => [styles.opcion, pressed && { backgroundColor: color.bg.surfaceSunken }]}>
      <View style={{ flex: 1, gap: 2 }}>
        <Txt v="labelL">{label}</Txt>
        <Txt v="caption" c={color.text.tertiary}>
          {detalle}
        </Txt>
      </View>
      <Txt v="headingL" c={color.text.tertiary}>
        ›
      </Txt>
    </Pressable>
  );
}

function TarjetaRuta({ ruta, onApartar }: { ruta: MiRuta; onApartar: () => void }) {
  return (
    <Card style={{ gap: space[3] }}>
      <View style={styles.rutaCabecera}>
        <Txt v="overline" c={color.text.tertiary}>
          MI RUTA
        </Txt>
        <EstadoBadge />
      </View>
      {ruta.estado === 'activa' ? (
        <>
          <Txt v="headingM">Tu ruta está activa</Txt>
          <View style={styles.calendario}>
            {ruta.hitos.map((h) => (
              <View key={h.fecha + h.detalle} style={styles.hito}>
                <View style={styles.hitoFecha}>
                  <Punto c={h.tipo === 'paga' ? color.estado.alDiaFg : color.estado.planFg} />
                  <Txt v="labelM">{h.fecha}</Txt>
                </View>
                <Txt v="caption" c={color.text.secondary}>
                  {h.detalle}
                </Txt>
              </View>
            ))}
          </View>
        </>
      ) : (
        <>
          <Txt v="headingM">Tu cobro ahora es el día {ruta.dia}</Txt>
          <View style={styles.apartar}>
            <Txt v="bodyS" c={color.text.secondary} style={{ flex: 1 }}>
              Aparta tu cuota en partes y el {ruta.dia} se paga sola.
            </Txt>
            <Button label="APARTAR" height={size.controlMd} onPress={onApartar} style={styles.activar} />
          </View>
        </>
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  saldo: { padding: space[5], gap: space[1], borderRadius: radius.lg, backgroundColor: color.bg.inverse, boxShadow: elevation[2] },
  otras: { flexDirection: 'row', flexWrap: 'wrap', gap: space[3] },
  cuenta: { flexGrow: 1, flexBasis: '45%', gap: space[1] },
  cuentaAncha: { flexBasis: '100%' },
  record: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  producto: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  productoTexto: { flex: 1, gap: space[1] },
  activar: { width: 104 },
  rutaCabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space[3] },
  calendario: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  hito: { flexGrow: 1, flexBasis: '30%', gap: space[1] },
  hitoFecha: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  apartar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  menu: { alignSelf: 'stretch', gap: space[2] },
  opcion: { minHeight: 56, flexDirection: 'row', alignItems: 'center', gap: space[3], paddingHorizontal: space[3], borderRadius: radius.md },
  confirmacion: { alignSelf: 'stretch', alignItems: 'center', gap: space[1] },
  condiciones: { alignSelf: 'stretch' },
  acciones: { alignSelf: 'stretch', gap: space[3] },
});
