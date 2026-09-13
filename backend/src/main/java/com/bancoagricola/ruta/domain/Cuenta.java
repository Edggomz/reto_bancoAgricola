package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUENTA")
@Getter
@Setter
@NoArgsConstructor
public class Cuenta {
  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(nullable = false)
  private String tipo;
  @Column(name = "product_name", nullable = false)
  private String productName;
  @Column(name = "number_masked", nullable = false)
  private String numberMasked;
  @Column(name = "number_full")
  private String numberFull;
  @Column(name = "balance_available", nullable = false)
  private BigDecimal balanceAvailable;
  /** Dinero congelado por un apartado: sigue siendo del cliente, no está disponible. */
  @Column(name = "balance_apartado", nullable = false)
  private BigDecimal balanceApartado = BigDecimal.ZERO;
  @Column(nullable = false)
  private String currency;
  @Column(name = "is_primary_source", nullable = false)
  private boolean primarySource;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
