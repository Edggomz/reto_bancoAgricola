import React from 'react';
import { Text, TextProps, TextStyle } from 'react-native';
import { color, text, TextVariant } from '../theme';

type Props = TextProps & {
  v: TextVariant;
  c?: string;
  align?: TextStyle['textAlign'];
  underline?: boolean;
  // cifras tabulares en los montos
  tabular?: boolean;
};

export function Txt({ v, c = color.text.primary, align, underline, tabular, style, ...rest }: Props) {
  return (
    <Text
      maxFontSizeMultiplier={1.3}
      // Corte de línea voraz, como Figma (Android por defecto reparte el párrafo).
      textBreakStrategy="simple"
      {...rest}
      style={[
        text[v],
        { color: c, includeFontPadding: false },
        align && { textAlign: align },
        underline && { textDecorationLine: 'underline' },
        tabular && { fontVariant: ['tabular-nums'] },
        style,
      ]}
    />
  );
}
