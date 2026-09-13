package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "IA_LLAMADA")
@Getter
@Setter
@NoArgsConstructor
public class IaLlamada {
  @Id
  private String id;

  @Column(nullable = false)
  private String servicio;

  private String proveedor;

  private String modelo;

  @Column(nullable = false)
  private boolean exito;

  @Column(nullable = false)
  private boolean fallback;

  @Column(name = "latencia_ms", nullable = false)
  private Long latenciaMs;

  private String error;

  private String guardrail;

  @Column(name = "sesion_id")
  private String sesionId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
