package com.bancoagricola.ruta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Contrato de la app móvil (final/src/api/tipos.ts). Forma EXACTA que consume el
 * frontend: montos como número, fechas y textos ya redactados. Los campos que
 * el frontend lee como «nulo» se envían como null explícito (sin NON_NULL).
 */
public final class App {
  private App() {}

  // ---- Ingreso ----
  public record Ingreso(String usuario, String clave) {}
  public record Token(String token) {}
  public record Perfil(String id, String usuario, String nombre, String iniciales, String colorAvatar,
                       String tarjeta, String arquetipo) {}

  // ---- Inicio (02 · 10 · 10b) ----
  public record Inicio(ClienteInicio cliente, CuentaInicio cuenta, List<CreditoInicio> creditos,
                       CreditoPrincipal credito, MiRuta ruta, RecordResumen record, List<Producto> productos,
                       int avisosSinLeer) {}
  public record ClienteInicio(String nombre, String iniciales) {}
  public record CuentaInicio(String producto, double saldo, String numero, double apartado) {}
  public record CreditoInicio(String id, String tipo, String titulo, double monto, String detalle, boolean apartable) {}
  public record CreditoPrincipal(String id, double cuota, int diaCobro, Cambio cambio) {}
  public record Cambio(int dia, String desdeMes) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MiRuta(String estado, List<Hito> hitos, Integer dia) {}
  public record Hito(String fecha, String detalle, String tipo) {}
  public record RecordResumen(int meses, String proximo) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Producto(String id, String titulo, String detalle, String ilustracion, String condiciones) {}

  // ---- Cambiar fecha de cobro (03 · 04 · 05) ----
  public record OpcionesFecha(int hoy, List<GrupoFecha> grupos) {}
  public record GrupoFecha(String titulo, List<DiaOpcion> dias) {}
  /** costo: qué pasa con el interés si elige ese día, ya redactado. interes > 0 solo si la cuota se corre. */
  public record DiaOpcion(int dia, String desde, String nota, String costo, double interes, int diasExtra) {}
  public record ConfirmarFecha(String frecuencia, Integer dia, String credito, Boolean aceptaInteres) {}
  public record FechaConfirmada(int dia, String desde, String operacion, String costo, double interes, int diasExtra) {}
  public record GuardarFrecuencia(String frecuencia) {}

  // ---- Apartar la cuota (06 · 07 · 08 · 09) ----
  public record CreditoApartable(String id, String nombre, String detalle, double monto, String nota, String ilustracion) {}
  public record OpcionesPartes(CreditoPartes credito, String sugerencia, List<OpcionPartes> opciones) {}
  public record CreditoPartes(String nombre, double cuota, int diaPago) {}
  public record OpcionPartes(int partes, List<FilaCalendario> calendario) {}
  public record FilaCalendario(String fecha, String detalle, double monto, String tipo) {}
  public record OrigenApartado(CuentaOrigen cuenta, List<String> pasos) {}
  public record CuentaOrigen(String titulo, String nombre, String detalle) {}
  public record ActivarApartado(String credito, Integer partes, Boolean automatico) {}
  public record Resumen(List<Fila> filas) {}
  public record Fila(String etiqueta, String valor) {}

  // ---- Abre tu cuenta (08a · 08b) ----
  public record OfertaCuenta(String nombre, List<String> condiciones) {}
  public record ContratoCuenta(List<Fila> filas, List<Documento> documentos) {}
  public record Documento(String titulo, String url) {}
  public record FirmarCuenta(Boolean acepta) {}

  // ---- Mi récord (12) ----
  public record Record(int meses, String proximo, List<HitoRecord> hitos, String explicacion, List<Suma> suma) {}
  public record HitoRecord(int n, String etiqueta, boolean hoy) {}
  public record Suma(String titulo, String detalle) {}

  // ---- Avisos (11) ----
  public record Aviso(String id, String titulo, String cuerpo, String fecha, Destino destino) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Destino(String vista, String aviso) {}

  // ---- Push ----
  public record RegistrarDispositivo(String token, String plataforma) {}

  // ---- Asesor (13 · 14 · 14b · 15) ----
  public record IniciarSesion(String aviso) {}
  public record MensajeAsesor(String id, String rol, String texto) {}
  public record RespuestaAsesor(List<MensajeAsesor> mensajes, List<String> sugerencias) {}
  public record SesionAsesor(String sesion, List<MensajeAsesor> mensajes, List<String> sugerencias) {}
  public record EnviarMensaje(String texto) {}

  // ---- IA del formulario: sugerencias (aditivo) ----
  public record Sugerencia(String opcion, String motivo, double confianza, String origen) {}
}
