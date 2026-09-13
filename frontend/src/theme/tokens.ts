// GENERADO por scripts/gen-tokens.mjs desde Figma «Ruta · Sistema y Pantallas».
// No editar a mano: exportar de nuevo desde Figma y correr el script.

export const color = {
  bg: {
    canvas: "#FFFFFF",
    surface: "#FFFFFF",
    surfaceSunken: "#F5F7F9",
    surfaceRaised: "#FFFFFF",
    inverse: "#2C2A29",
    brand: "#FDDA24",
    scrim: "#1A1B1A",
    avatar: "#7E4FBC"
  },
  text: {
    primary: "#1A1B1A",
    secondary: "#5B5B5B",
    tertiary: "#6D6E72",
    inverse: "#FFFFFF",
    onBrand: "#2C2A29",
    link: "#1A1B1A"
  },
  border: {
    subtle: "#E6E7E8",
    default: "#C4C4C4",
    strong: "#2C2A29",
    focus: "#00448C"
  },
  action: {
    primaryBg: "#FDDA24",
    primaryBgPressed: "#E0C01B",
    primaryFg: "#2C2A29",
    secondaryBg: "#FFFFFF",
    secondaryFg: "#1A1B1A",
    secondaryBorder: "#2C2A29",
    disabledBg: "#E6E7E8",
    disabledFg: "#6D6E72"
  },
  estado: {
    alDiaAccent: "#00C389",
    alDiaFg: "#00714E",
    alDiaBg: "#E0F7EF",
    planAccent: "#59CBE8",
    planFg: "#00448C",
    planBg: "#E4F6FC",
    atrasoAccent: "#FF7F41",
    atrasoFg: "#A8420F",
    atrasoBg: "#FFEDE4",
    quiebreAccent: "#D4351C",
    quiebreFg: "#B02D18",
    quiebreBg: "#FBE6E2",
    neutroAccent: "#C4C4C4",
    neutroFg: "#5B5B5B",
    neutroBg: "#F4F4F4"
  }
} as const;

export const space = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
  24: 96
} as const;

export const radius = {
  none: 0,
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  full: 999
} as const;

export const size = {
  controlSm: 36,
  controlMd: 44,
  controlLg: 52,
  iconSm: 16,
  iconMd: 24,
  iconLg: 32,
  frameMobileW: 390,
  frameMobileH: 844,
  gutterMobile: 20,
  safeTop: 59,
  safeBottom: 34,
  frameDesktopW: 1440,
  gutterDesktop: 24,
  marginDesktop: 80
} as const;

/** Los 15 estilos de texto. letterSpacing ya viene en px. */
export const text = {
  caption: {
    fontFamily: "OpenSans_400Regular",
    fontSize: 12,
    lineHeight: 16,
    letterSpacing: 0
  },
  overline: {
    fontFamily: "OpenSans_700Bold",
    fontSize: 11,
    lineHeight: 16,
    letterSpacing: 0.88
  },
  displayXl: {
    fontFamily: "OpenSans_700Bold",
    fontSize: 40,
    lineHeight: 44,
    letterSpacing: -0.8
  },
  displayL: {
    fontFamily: "OpenSans_700Bold",
    fontSize: 32,
    lineHeight: 38,
    letterSpacing: -0.48
  },
  headingXl: {
    fontFamily: "OpenSans_700Bold",
    fontSize: 24,
    lineHeight: 30,
    letterSpacing: -0.24
  },
  headingL: {
    fontFamily: "OpenSans_700Bold",
    fontSize: 20,
    lineHeight: 26,
    letterSpacing: -0.1
  },
  headingM: {
    fontFamily: "OpenSans_600SemiBold",
    fontSize: 17,
    lineHeight: 24,
    letterSpacing: 0
  },
  bodyL: {
    fontFamily: "OpenSans_400Regular",
    fontSize: 16,
    lineHeight: 24,
    letterSpacing: 0
  },
  bodyM: {
    fontFamily: "OpenSans_400Regular",
    fontSize: 15,
    lineHeight: 22,
    letterSpacing: 0
  },
  bodyS: {
    fontFamily: "OpenSans_400Regular",
    fontSize: 13,
    lineHeight: 18,
    letterSpacing: 0
  },
  labelL: {
    fontFamily: "OpenSans_600SemiBold",
    fontSize: 15,
    lineHeight: 20,
    letterSpacing: 0
  },
  labelM: {
    fontFamily: "OpenSans_600SemiBold",
    fontSize: 13,
    lineHeight: 16,
    letterSpacing: 0
  },
  numeralXl: {
    fontFamily: "OpenSans_300Light",
    fontSize: 40,
    lineHeight: 44,
    letterSpacing: -0.4
  },
  numeralL: {
    fontFamily: "OpenSans_300Light",
    fontSize: 28,
    lineHeight: 34,
    letterSpacing: 0
  },
  numeralM: {
    fontFamily: "OpenSans_600SemiBold",
    fontSize: 20,
    lineHeight: 26,
    letterSpacing: 0
  }
} as const;

/** elevation/1–3 como boxShadow. */
export const elevation = {
  1: "0px 1px 3px 0px rgba(26, 27, 26, 0.08)",
  2: "0px 4px 12px 0px rgba(26, 27, 26, 0.1)",
  3: "0px -4px 24px 0px rgba(26, 27, 26, 0.14)"
} as const;

export type TextVariant = keyof typeof text;
