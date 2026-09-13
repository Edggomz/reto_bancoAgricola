package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "APARTADO_CUOTA")
@IdClass(ApartadoCuota.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ApartadoCuota {
  public static final String PENDIENTE = "pendiente";
  public static final String APARTADA = "apartada";
  public static final String NO_ALCANZO = "no_alcanzo";

  @Id
  @Column(name = "apartado_id")
  private String apartadoId;
  @Id
  private Integer idx;
  @Column(nullable = false)
  private String label;
  @Column(nullable = false)
  private LocalDate fecha;
  @Column(nullable = false)
  private BigDecimal amount;
  @Column(nullable = false)
  private String estado = PENDIENTE;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String apartadoId;
    private Integer idx;
  }
}
