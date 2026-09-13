package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.ParametroApp;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Copy del producto y umbrales, leídos de PARAMETRO_APP (nada de esto vive en el código). */
@Service
public class CopyService {
  private static final Pattern VARIABLE = Pattern.compile("\\{(\\w+)}");

  private final Repositorios.ParametrosApp parametros;
  private volatile Map<String, String> cache;

  public CopyService(Repositorios.ParametrosApp parametros) {
    this.parametros = parametros;
  }

  public String texto(String clave) {
    String valor = valores().get(clave);
    if (valor == null) {
      throw new IllegalStateException("Falta el parámetro '" + clave + "' en PARAMETRO_APP");
    }
    return valor;
  }

  public String render(String clave, Map<String, ?> variables) {
    return reemplazar(texto(clave), variables);
  }

  /** Para parámetros que pueden no existir (p. ej. los de demo en una base sin volver a sembrar). */
  public java.util.Optional<String> opcional(String clave) {
    return java.util.Optional.ofNullable(valores().get(clave)).map(String::trim).filter(s -> !s.isEmpty());
  }

  public int entero(String clave) {
    return Integer.parseInt(texto(clave).trim());
  }

  public List<String> lista(String clave) {
    return Arrays.stream(texto(clave).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public void recargar() {
    cache = null;
  }

  public static String reemplazar(String plantilla, Map<String, ?> variables) {
    Matcher m = VARIABLE.matcher(plantilla);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      Object valor = variables.get(m.group(1));
      m.appendReplacement(out, Matcher.quoteReplacement(valor == null ? m.group() : String.valueOf(valor)));
    }
    m.appendTail(out);
    return out.toString();
  }

  private Map<String, String> valores() {
    Map<String, String> actual = cache;
    if (actual == null) {
      actual = parametros.findAll().stream()
          .collect(Collectors.toUnmodifiableMap(ParametroApp::getClave, ParametroApp::getValor));
      cache = actual;
    }
    return actual;
  }
}
