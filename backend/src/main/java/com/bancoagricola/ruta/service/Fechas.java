package com.bancoagricola.ruta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/** Formato de fechas y montos para el copy («30 de septiembre», «30 sep», «$1,482.14»). */
public final class Fechas {
  private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
      "septiembre", "octubre", "noviembre", "diciembre"};
  private static final String[] CORTOS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
  private static final String[] DIAS = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};

  private Fechas() {}

  /** «30 de septiembre». */
  public static String etiqueta(LocalDate fecha) {
    return fecha.getDayOfMonth() + " de " + mes(fecha);
  }

  /** «18 de octubre de 2026». */
  public static String etiquetaAnio(LocalDate fecha) {
    return etiqueta(fecha) + " de " + fecha.getYear();
  }

  /** «30 sep». */
  public static String corta(LocalDate fecha) {
    return fecha.getDayOfMonth() + " " + CORTOS[fecha.getMonthValue() - 1];
  }

  /** «Feb 2027». */
  public static String mesAnio(LocalDate fecha) {
    String m = CORTOS[fecha.getMonthValue() - 1];
    return Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + fecha.getYear();
  }

  public static String mes(LocalDate fecha) {
    return MESES[fecha.getMonthValue() - 1];
  }

  public static String monto(BigDecimal valor) {
    return String.format(Locale.US, "$%,.2f", valor.setScale(2, RoundingMode.HALF_UP));
  }

  /** Primera fecha estrictamente posterior a {@code desde} con ese día del mes (ajustado a meses cortos). */
  public static LocalDate proximoDia(LocalDate desde, int dia) {
    LocalDate candidata = conDia(YearMonth.from(desde), dia);
    return candidata.isAfter(desde) ? candidata : conDia(YearMonth.from(desde).plusMonths(1), dia);
  }

  /** Primera fecha igual o posterior a {@code minimo} con ese día del mes. */
  public static LocalDate desdeDia(LocalDate minimo, int dia) {
    return proximoDia(minimo.minusDays(1), dia);
  }

  public static LocalDate conDia(YearMonth mes, int dia) {
    return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
  }

  public static DayOfWeek diaSemana(String nombre) {
    if (nombre == null) return DayOfWeek.FRIDAY;
    String n = nombre.toLowerCase(Locale.ROOT).replace('é', 'e').replace('á', 'a');
    for (int i = 0; i < DIAS.length; i++) {
      if (DIAS[i].equals(n)) return DayOfWeek.of(i + 1);
    }
    return DayOfWeek.FRIDAY;
  }

  public static String minusculaInicial(String texto) {
    return texto == null || texto.isEmpty() ? texto : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
  }

  /** «a, b y c». */
  public static String enumerar(List<String> partes) {
    if (partes.isEmpty()) return "";
    if (partes.size() == 1) return partes.get(0);
    return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
  }

  /** «30 y el 15» (el primer «el» lo pone la frase: «El 30 y el 15 apartamos…», «$124.25 el 30 y el 15»). */
  public static String enumerarDias(List<LocalDate> fechas) {
    List<String> partes = new java.util.ArrayList<>();
    for (int i = 0; i < fechas.size(); i++) {
      partes.add((i == 0 ? "" : "el ") + fechas.get(i).getDayOfMonth());
    }
    return enumerar(partes);
  }

  public static double d(BigDecimal valor) {
    return valor == null ? 0 : valor.setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}
