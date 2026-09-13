import React, { useState } from 'react';
import { Animated, Platform, StyleSheet, TextInput, View } from 'react-native';
import { AppBar, Body, Button, Footer, Logo, Screen, Txt, useSacudida } from '../../components';
import { guardarToken, hayApi } from '../../api/cliente';
import { api } from '../../api/servicios';
import type { ScreenProps } from '../../navigation/types';
import { color, space, text } from '../../theme';

export function Ingreso({ navigation }: ScreenProps<'Ingreso'>) {
  const [usuario, setUsuario] = useState('');
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const { estilo, sacudir } = useSacudida();

  const continuar = async () => {
    if (!hayApi()) return navigation.navigate('Inicio');
    if (!usuario.trim() || !clave) {
      sacudir();
      return setError('Escribe tu usuario y tu clave.');
    }
    setEnviando(true);
    setError(null);
    try {
      const { token } = await api.ingresar(usuario.trim(), clave);
      guardarToken(token);
      navigation.navigate('Inicio');
    } catch {
      sacudir();
      setError('No pudimos ingresar. Revisa tu usuario y tu clave.');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Screen>
      <AppBar
        padX={space[5]}
        left={<View />}
        center={<Logo onLongPress={() => navigation.navigate('Demo')} />}
        right={
          <Txt v="headingM" c={color.text.secondary}>
            ?
          </Txt>
        }
      />
      <Body gap={space[6]}>
        <Txt v="headingM" align="center">
          {'Bienvenido a la aplicación\nde Banco Agrícola'}
        </Txt>
        <Animated.View style={[{ gap: space[6] }, estilo]}>
          <Campo label="Usuario" value={usuario} onChangeText={setUsuario} />
          <Campo label="Clave" value={clave} onChangeText={setClave} secure onSubmit={continuar} />
        </Animated.View>
        <View style={styles.aviso}>
          {error ? (
            <Txt v="bodyS" c={color.estado.quiebreFg}>
              {error}
            </Txt>
          ) : null}
        </View>
        <Txt v="labelL" c={color.text.link} underline align="center">
          ¿Olvidaste o bloqueaste tu usuario o clave?
        </Txt>
      </Body>
      <Footer gap={space[4]} bottom={30}>
        <Txt v="labelL" c={color.text.link} underline align="center">
          Ingresar con huella o Face ID
        </Txt>
        <Button label={enviando ? 'INGRESANDO…' : 'CONTINUAR'} onPress={enviando ? undefined : continuar} />
      </Footer>
    </Screen>
  );
}

function Campo({
  label,
  value,
  onChangeText,
  secure,
  onSubmit,
}: {
  label: string;
  value: string;
  onChangeText: (t: string) => void;
  secure?: boolean;
  onSubmit?: () => void;
}) {
  return (
    <View style={{ gap: 6 }}>
      <Txt v="bodyS" c={color.text.secondary}>
        {label}
      </Txt>
      <View style={styles.linea}>
        <TextInput
          value={value}
          onChangeText={onChangeText}
          secureTextEntry={secure}
          autoCapitalize="none"
          autoCorrect={false}
          accessibilityLabel={label}
          returnKeyType={secure ? 'go' : 'next'}
          onSubmitEditing={onSubmit}
          style={styles.input}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  linea: { height: 34, justifyContent: 'center', borderBottomWidth: 1.5, borderBottomColor: color.border.strong },
  input: {
    ...text.bodyL,
    color: color.text.primary,
    padding: 0,
    height: 24,
    includeFontPadding: false,
    ...(Platform.OS === 'web' ? { outlineStyle: 'none' } : null),
  } as object,
  aviso: { minHeight: 20, justifyContent: 'center' },
});
