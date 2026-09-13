package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Una línea del registro de auditoría: qué pasó, cuándo, por qué canal y a quién. */
@Entity
@Table(name = "EVENTO_AUDITORIA")
@Getter
@Setter
@NoArgsConstructor
public class EventoAuditoria {
  @Id
  private String id;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
  @Column(nullable = false)
  private String canal;
  @Column(nullable = false)
  private String tipo;
  @Column(nullable = false)
  private String nivel;
  @Column(name = "cliente_id")
  private String clienteId;
  private String referencia;
  @Column(nullable = false)
  private String resumen;
  /** JSON con los datos del evento. */
  @Lob
  private String detalle;
  @Column(name = "duracion_ms")
  private Integer duracionMs;
}
