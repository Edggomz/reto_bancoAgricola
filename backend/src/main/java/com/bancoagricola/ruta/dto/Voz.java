package com.bancoagricola.ruta.dto;

import java.util.List;
import java.util.Map;

/**
 * Contrato del canal de voz (n8n ⇄ backend). No sabe de Vapi: el adaptador del
 * proveedor vive en n8n. El cliente SIEMPRE llega desde los metadatos de la
 * llamada que programó el banco, nunca desde lo que se dice en la llamada.
 * Los campos «decir» traen el texto ya redactado para que el modelo no invente
 * montos ni fechas.
 */
public final class Voz {
  private Voz() {}

  // ---- Llamadas salientes ----
  public record Pendientes(boolean ventanaAbierta, String ventana, List<Pendiente> clientes) {}
  public record Pendiente(String clienteId, String nombre, int diasParaCobro, boolean usaApp, int intentos) {}
  public record Programar(String clienteId) {}
  public record Programada(String llamadaId, String telefono, Map<String, Object> variables) {}

  // ---- Herramientas del agente durante la llamada ----
  /** diasIngreso: días del mes en que le entra el dinero (31 = fin de mes). */
  public record PedirPropuesta(String llamadaId, String clienteId, String tipoIngreso, List<Integer> diasIngreso,
                               Boolean semanal, Boolean constante) {}
  public record Propuesta(boolean bloqueado, String decir, List<OpcionVoz> opciones) {}
  public record OpcionVoz(int dia, String desde, int diasExtra, double interes, String decir) {}
  public record ConfirmarFecha(String llamadaId, String clienteId, String tipoIngreso, List<Integer> diasIngreso,
                               Boolean semanal, Boolean constante, Integer dia, Boolean aceptaInteres) {}
  public record Confirmado(int dia, String desde, int diasExtra, double interes, String cambiableDesde, String decir) {}

  // ---- Fin de la llamada ----
  public record Resultado(String llamadaId, String clienteId, String proveedorId, String motivoFin, String resultado,
                          String tipoIngreso, Boolean constante, List<Integer> diasIngreso, String canalPago,
                          Boolean usaBancaLinea, String resumen, String transcripcion, Integer duracionSeg) {}
}
