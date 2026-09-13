import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { AppBar, Button, Chip, Circulo, Hueco, IconButton, Screen, Sheet, Title, Txt, unToque, useBottom, useKeyboardHeight, useSacudida } from '../../components';
import { hayApi } from '../../api/cliente';
import { api } from '../../api/servicios';
import type { RespuestaAsesor } from '../../api/tipos';
import { hora } from '../../formato';
import type { ScreenProps } from '../../navigation/types';
import { color, radius, size, space, text } from '../../theme';
import { Conversacion, conversacionActual, guardarConversacion, Mensaje, nuevaConversacion } from './sesion';

let secuencia = 0;
const idLocal = () => `local-${Date.now()}-${secuencia++}`;

/** 13, 14 y 14b: el asesor. Todo lo que dice llega del backend; sigue hasta que la persona lo termina (15). */
export function Asesor({ navigation, route }: ScreenProps<'Asesor'>) {
  const aviso = route.params?.aviso ?? null;
  const [conv, setConv] = useState<Conversacion>(() => {
    const previa = conversacionActual();
    return previa && (aviso === null || previa.aviso === aviso) ? previa : nuevaConversacion(aviso);
  });
  const [esperando, setEsperando] = useState(false);
  const [sinConexion, setSinConexion] = useState(false);
  const [texto, setTexto] = useState('');
  const [terminar, setTerminar] = useState(false);
  const scroll = useRef<ScrollView>(null);
  const bottom = useBottom();
  const teclado = useKeyboardHeight();
  const { estilo: estiloCampo, sacudir } = useSacudida();

  const actualizar = useCallback((cambio: (c: Conversacion) => Conversacion) => {
    setConv((c) => {
      const n = cambio(c);
      guardarConversacion(n);
      return n;
    });
  }, []);

  const recibir = useCallback(
    (r: RespuestaAsesor, sesion?: string) =>
      actualizar((c) => ({ ...c, sesion: sesion ?? c.sesion, mensajes: [...c.mensajes, ...r.mensajes], sugerencias: r.sugerencias ?? [] })),
    [actualizar],
  );

  const iniciar = useCallback(async () => {
    if (!hayApi()) return;
    setEsperando(true);
    setSinConexion(false);
    try {
      const s = await api.asesor.iniciar(aviso ?? undefined);
      recibir(s, s.sesion);
    } catch {
      setSinConexion(true);
    } finally {
      setEsperando(false);
    }
  }, [aviso, recibir]);

  useEffect(() => {
    guardarConversacion(conv);
    if (!conv.sesion && conv.mensajes.length === 0) iniciar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // otro aviso abierto con el asesor ya en pantalla: empieza una conversación con ese contexto
  useEffect(() => {
    if (aviso === null || aviso === conv.aviso) return;
    if (conv.sesion) api.asesor.terminar(conv.sesion).catch(() => {});
    const nueva = nuevaConversacion(aviso);
    guardarConversacion(nueva);
    setConv(nueva);
    iniciar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aviso]);

  const enviar = async (contenido: string, reintento?: Mensaje) => {
    const limpio = contenido.trim();
    if (!limpio || esperando) return;
    const propio: Mensaje = reintento ?? { id: idLocal(), rol: 'persona', texto: limpio };
    actualizar((c) => ({
      ...c,
      sugerencias: [],
      mensajes: reintento ? c.mensajes.map((m) => (m.id === propio.id ? { ...m, fallo: false } : m)) : [...c.mensajes, propio],
    }));
    setTexto('');
    setEsperando(true);
    try {
      if (!conv.sesion) throw new Error('sin sesión');
      recibir(await api.asesor.enviar(conv.sesion, limpio));
    } catch {
      actualizar((c) => ({ ...c, mensajes: c.mensajes.map((m) => (m.id === propio.id ? { ...m, fallo: true } : m)) }));
    } finally {
      setEsperando(false);
    }
  };

  const cerrarDelTodo = () => {
    if (conv.sesion) api.asesor.terminar(conv.sesion).catch(() => {});
    guardarConversacion(null);
    navigation.goBack();
  };
  // sin nada escrito no hay conversación que perder: × cierra directo
  const tocarCerrar = () => (conv.mensajes.some((m) => m.rol === 'persona') ? setTerminar(true) : cerrarDelTodo());

  const ultimoEsAsesor = conv.mensajes.length > 0 && conv.mensajes[conv.mensajes.length - 1].rol === 'asesor';
  return (
    <Screen>
      <AppBar
        left={<IconButton glyph="‹" label="Volver" onPress={navigation.goBack} />}
        center={<Title subtitle="Asistente con IA">Asesor bancario</Title>}
        right={<IconButton glyph="×" label="Terminar conversación" onPress={tocarCerrar} />}
      />
      <View style={{ flex: 1 }}>
        <ScrollView
          ref={scroll}
          style={styles.contenido}
          contentContainerStyle={styles.hilo}
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => scroll.current?.scrollToEnd({ animated: true })}
        >
          <Txt v="caption" c={color.text.tertiary} align="center">
            Hoy · {hora(conv.inicio)}
          </Txt>
          <View style={styles.avisoIA}>
            <Txt v="caption" c={color.text.secondary} align="center" style={{ flex: 1 }}>
              Te atiende un asesor con IA. Responde con los datos de tus productos.
            </Txt>
          </View>
          {conv.mensajes.map((m) => (m.rol === 'asesor' ? <DelAsesor key={m.id}>{m.texto}</DelAsesor> : <Propio key={m.id} mensaje={m} onReintentar={() => enviar(m.texto, m)} />))}
          {!hayApi() && conv.mensajes.length === 0 ? <BurbujaVacia /> : null}
          {esperando ? <Escribiendo /> : null}
          {sinConexion ? (
            <Pressable onPress={iniciar} style={styles.reintento} accessibilityRole="button">
              <Txt v="bodyS" c={color.text.secondary}>
                No pudimos conectar con tu asesor.{' '}
                <Txt v="labelM" c={color.text.link} underline>
                  Reintentar
                </Txt>
              </Txt>
            </Pressable>
          ) : null}
          {!esperando && ultimoEsAsesor && conv.sugerencias.length ? (
            <View style={styles.sugeridas}>
              {conv.sugerencias.map((s) => (
                <Chip key={s} label={s} onPress={() => enviar(s)} />
              ))}
            </View>
          ) : null}
        </ScrollView>
        <View style={[styles.escribir, { paddingBottom: teclado > 0 ? teclado + space[3] : bottom }]}>
          <Animated.View style={[styles.campo, estiloCampo]}>
            <TextInput
              value={texto}
              onChangeText={setTexto}
              placeholder="Escribe tu pregunta"
              placeholderTextColor={color.text.tertiary}
              returnKeyType="send"
              onSubmitEditing={() => enviar(texto)}
              maxLength={500}
              style={styles.input}
            />
          </Animated.View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Enviar"
            onPress={() => (texto.trim() ? enviar(texto) : sacudir())}
            style={({ pressed }) => [pressed && { transform: [{ scale: 0.92 }], opacity: 0.85 }]}
          >
            <Circulo d={size.controlMd} bg={color.bg.inverse}>
              <Txt v="headingM" c={color.text.inverse}>
                ↑
              </Txt>
            </Circulo>
          </Pressable>
        </View>
      </View>

      <Sheet visible={terminar} onRequestClose={() => setTerminar(false)} cerrarAlTocarFuera>
        <View style={styles.confirmacion}>
          <Txt v="headingXl" align="center">
            ¿Terminamos la conversación?
          </Txt>
          <Txt v="bodyS" c={color.text.tertiary} align="center">
            Cuando quieras, vuelves con el botón «Asesor».
          </Txt>
        </View>
        <View style={styles.acciones}>
          <Button label="TERMINAR" onPress={cerrarDelTodo} />
          <Button label="SEGUIR CONVERSANDO" variant="secundario" onPress={unToque(() => setTerminar(false))} />
        </View>
      </Sheet>
    </Screen>
  );
}

function DelAsesor({ children }: { children: string }) {
  return (
    <View style={[styles.burbuja, styles.ia]}>
      <Txt v="bodyM">{children}</Txt>
    </View>
  );
}

function Propio({ mensaje, onReintentar }: { mensaje: Mensaje; onReintentar: () => void }) {
  return (
    <View style={{ alignItems: 'flex-end', gap: space[1] }}>
      <View style={[styles.burbuja, styles.tuyo, mensaje.fallo && { opacity: 0.6 }]}>
        <Txt v="bodyM" c={color.text.inverse}>
          {mensaje.texto}
        </Txt>
      </View>
      {mensaje.fallo ? (
        <Pressable onPress={onReintentar} accessibilityRole="button" style={styles.reintento}>
          <Txt v="caption" c={color.text.secondary}>
            No se envió.{' '}
            <Txt v="caption" c={color.text.link} underline>
              Reintentar
            </Txt>
          </Txt>
        </Pressable>
      ) : null}
    </View>
  );
}

/** Hueco del saludo mientras no hay backend. */
function BurbujaVacia() {
  return (
    <View style={[styles.burbuja, styles.ia, { width: 260, gap: space[2] }]}>
      <Hueco ancho="85%" alto={11} />
      <Hueco ancho="55%" alto={11} />
    </View>
  );
}

/** Tres puntos mientras el asesor responde. */
function Escribiendo() {
  const t = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(Animated.timing(t, { toValue: 3, duration: 900, easing: Easing.linear, useNativeDriver: true }));
    loop.start();
    return () => loop.stop();
  }, [t]);
  return (
    <View style={[styles.burbuja, styles.ia, styles.escribiendo]} accessibilityLabel="El asesor está escribiendo">
      {[0, 1, 2].map((i) => (
        <Animated.View
          key={i}
          style={[styles.puntito, { opacity: t.interpolate({ inputRange: [0, 0.5, 1, 1.5, 2, 2.5, 3], outputRange: [0, 1, 2, 3, 4, 5, 6].map((k) => (k === 2 * i + 1 ? 1 : 0.3)) }) }]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  contenido: { flex: 1, backgroundColor: color.bg.surfaceSunken },
  hilo: { flexGrow: 1, justifyContent: 'flex-end', padding: space[4], gap: space[3] },
  avisoIA: { flexDirection: 'row', paddingVertical: space[2], paddingHorizontal: space[3], borderRadius: radius.sm, backgroundColor: color.bg.surface },
  burbuja: { maxWidth: 296, paddingVertical: space[3], paddingHorizontal: space[4] },
  ia: {
    alignSelf: 'flex-start',
    backgroundColor: color.bg.surface,
    borderWidth: 1,
    borderColor: color.border.subtle,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    borderBottomRightRadius: radius.lg,
    borderBottomLeftRadius: radius.xs,
  },
  tuyo: {
    alignSelf: 'flex-end',
    backgroundColor: color.bg.inverse,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    borderBottomRightRadius: radius.xs,
    borderBottomLeftRadius: radius.lg,
  },
  escribiendo: { flexDirection: 'row', gap: 6, paddingVertical: space[4] },
  puntito: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.text.tertiary },
  reintento: { minHeight: 32, justifyContent: 'center' },
  sugeridas: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  escribir: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    paddingTop: space[3],
    paddingHorizontal: space[4],
    borderTopWidth: 1,
    borderTopColor: color.border.subtle,
    backgroundColor: color.bg.surface,
  },
  campo: {
    flex: 1,
    height: size.controlMd,
    justifyContent: 'center',
    paddingHorizontal: space[4],
    borderWidth: 1,
    borderColor: color.border.default,
    borderRadius: radius.full,
    backgroundColor: color.bg.surfaceSunken,
  },
  input: { ...text.bodyM, color: color.text.primary, padding: 0, includeFontPadding: false, ...(Platform.OS === 'web' ? { outlineStyle: 'none' } : null) } as object,
  confirmacion: { alignSelf: 'stretch', alignItems: 'center', gap: space[1] },
  acciones: { alignSelf: 'stretch', gap: space[3] },
});
