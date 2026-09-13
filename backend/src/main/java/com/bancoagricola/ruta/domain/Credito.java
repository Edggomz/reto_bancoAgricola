package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Productos bancarios: tarjeta de crédito (card) y créditos con cuota (personal, hipotecario, bancario). */
@Entity
@Table(name = "CREDITO")
@Getter
@Setter
@NoArgsConstructor
public class Credito {
  public static final String TARJETA = "card";
  public static final String PERSONAL = "personal";
  public static final String HIPOTECARIO = "hipotecario";
  public static final String BANCARIO = "bancario";
  public static final String AL_DIA = "al_dia";
  public static final String PARTE_PENDIENTE = "parte_pendiente";

  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(nullable = false)
  private String kind;
  @Column(nullable = false)
  private String name;
  @Column(name = "number_masked", nullable = false)
  private String numberMasked;
  @Column(nullable = false)
  private String currency;
  @Column(nullable = false)
  private boolean apartable;
  @Column(name = "credit_limit")
  private BigDecimal creditLimit;
  private BigDecimal available;
  @Column(name = "used_pct")
  private Integer usedPct;
  @Column(name = "pay_contado")
  private BigDecimal payContado;
  @Column(name = "installment_amount")
  private BigDecimal installmentAmount;
  @Column(name = "current_due_day")
  private Integer currentDueDay;
  @Column(name = "operation_number")
  private String operationNumber;
  @Column(name = "dia_corte", nullable = false)
  private Integer diaCorte;
  @Column(name = "fecha_apertura", nullable = false)
  private LocalDate fechaApertura;
  @Column(name = "estado_pago", nullable = false)
  private String estadoPago;

  public boolean esTarjeta() {
    return TARJETA.equals(kind);
  }

  /** Créditos con cuota mensual fija: los que pueden mover su fecha de cobro. */
  public boolean tieneCuota() {
    return !esTarjeta() && installmentAmount != null;
  }

  /** Lo que se reparte al apartar: la cuota del crédito o el pago de contado de la tarjeta. */
  public BigDecimal montoApartable() {
    return esTarjeta() ? payContado : installmentAmount;
  }

  /** Día del mes en que vence el pago: la cuota del crédito o el corte de la tarjeta. */
  public int diaDeCobro() {
    return esTarjeta() || currentDueDay == null ? diaCorte : currentDueDay;
  }

  public String ultimos4() {
    String n = numberMasked == null ? "" : numberMasked;
    return n.length() >= 4 ? n.substring(n.length() - 4) : n;
  }
}
