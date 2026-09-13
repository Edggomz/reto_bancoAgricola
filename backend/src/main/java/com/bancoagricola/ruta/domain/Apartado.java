package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "APARTADO")
@Getter
@Setter
@NoArgsConstructor
public class Apartado {
  public static final String ACTIVO = "activo";
  public static final String CANCELADO = "cancelado";

  @Id
  private String id;

  @Column(name = "credito_id", nullable = false)
  private String creditoId;

  @Column(nullable = false)
  private Integer parts;

  @Column(name = "source_account_id", nullable = false)
  private String sourceAccountId;

  @Column(name = "pays_on_label", nullable = false)
  private String paysOnLabel;

  @Column(nullable = false)
  private boolean automatic;

  @Column(name = "first_full_installment_label", nullable = false)
  private String firstFullInstallmentLabel;

  @Column(name = "monto_total", nullable = false)
  private BigDecimal montoTotal;

  @Column(name = "fecha_pago", nullable = false)
  private LocalDate fechaPago;

  @Column(nullable = false)
  private String estado;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
