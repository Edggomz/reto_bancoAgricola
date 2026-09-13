package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "OFERTA_APERTURA")
@Getter
@Setter
@NoArgsConstructor
public class OfertaApertura {
  @Id
  private String id;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "account_product_name", nullable = false)
  private String accountProductName;

  @Column(name = "account_tipo", nullable = false)
  private String accountTipo;

  @Column(name = "opening_cost", nullable = false)
  private BigDecimal openingCost;

  @Column(name = "monthly_cost", nullable = false)
  private BigDecimal monthlyCost;

  @Column(nullable = false)
  private boolean activo;
}
