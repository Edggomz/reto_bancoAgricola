package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Bitácora de envíos push. SIMULADO = sin credenciales de Firebase ni token de Expo. */
@Entity
@Table(name = "NOTIFICACION_ENVIADA")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionEnviada {
  public static final String ENVIADO = "ENVIADO";
  public static final String SIMULADO = "SIMULADO";
  public static final String ERROR = "ERROR";

  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(name = "credito_id")
  private String creditoId;
  @Column(name = "dispositivo_id")
  private String dispositivoId;
  @Column(name = "aviso_id")
  private String avisoId;
  @Column(nullable = false)
  private String canal = "simulado";
  @Column(nullable = false)
  private String tipo;
  @Column(nullable = false)
  private String titulo;
  @Column(nullable = false)
  private String cuerpo;
  @CreationTimestamp
  @Column(name = "fecha_envio", nullable = false, updatable = false)
  private LocalDateTime fechaEnvio;
  @Column(name = "corte_fecha", nullable = false)
  private LocalDate corteFecha;
  @Column(name = "dias_antes_corte", nullable = false)
  private Integer diasAntesCorte;
  @Column(nullable = false)
  private String estado;
  @Column(name = "detalle_error")
  private String detalleError;
}
