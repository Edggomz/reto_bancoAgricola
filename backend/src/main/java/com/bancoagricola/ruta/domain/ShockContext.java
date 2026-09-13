package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "SHOCK_CONTEXT")
@Getter
@Setter
@NoArgsConstructor
public class ShockContext {
  @Id
  @Column(name = "cliente_id")
  private String clienteId;

  @Column(name = "credito_id")
  private String creditoId;

  @Column(name = "event_label", nullable = false)
  private String eventLabel;

  @Column(nullable = false)
  private String reassurance;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  @Column(name = "next_date_label", nullable = false)
  private String nextDateLabel;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
