package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cada llamada del agente de voz. El proveedor (Vapi) guarda el audio; aquí queda
 * lo que el banco audita: a quién, qué intento, qué se acordó y por qué terminó.
 * El resultado «fecha_cambiada» lo pone el servidor al confirmar, no el modelo.
 */
@Entity
@Table(name = "LLAMADA_VOZ")
@Getter
@Setter
@NoArgsConstructor
public class LlamadaVoz {
  public static final String PROGRAMADA = "programada";
  public static final String TERMINADA = "terminada";
  public static final String FALLIDA = "fallida";

  public static final String FECHA_CAMBIADA = "fecha_cambiada";
  public static final String SIN_CAMBIO = "sin_cambio";
  public static final String BLOQUEADO = "bloqueado";
  public static final String NO_CONTESTO = "no_contesto";
  public static final String BUZON = "buzon";
  public static final String TERCERO = "tercero";
  public static final String VOLVER_A_LLAMAR = "volver_a_llamar";
  public static final String NO_LLAMAR = "no_llamar";

  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(name = "credito_id")
  private String creditoId;
  @Column(nullable = false)
  private String telefono;
  @Column(nullable = false)
  private String proveedor;
  @Column(name = "proveedor_id")
  private String proveedorId;
  @Column(nullable = false)
  private Integer intento;
  @Column(nullable = false)
  private String estado;
  private String resultado;
  @Column(name = "motivo_fin")
  private String motivoFin;
  @Column(name = "dia_nuevo")
  private Integer diaNuevo;
  @Column(name = "dias_extra")
  private Integer diasExtra;
  @Column(name = "interes_extra")
  private BigDecimal interesExtra;
  private String resumen;
  @Lob
  private String transcripcion;
  @Column(name = "duracion_seg")
  private Integer duracionSeg;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
  @Column(name = "ended_at")
  private LocalDateTime endedAt;
}
