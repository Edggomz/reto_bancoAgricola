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

@Entity
@Table(name = "CATALOGO_FRECUENCIA_DIA")
@IdClass(CatalogoFrecuenciaDia.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class CatalogoFrecuenciaDia {
  @Id
  @Column(name = "frecuencia_id")
  private String frecuenciaId;
  @Id
  private Integer idx;
  @Column(name = "dia_pago")
  private Integer diaPago;
  @Column(name = "fin_de_mes", nullable = false)
  private boolean finDeMes;
  @Column(name = "dia_semana")
  private String diaSemana;
  @Column(name = "opcion_id")
  private String opcionId;
  private String label;
  private String hint;
  private String titulo;
  @Column(name = "pago_label")
  private String pagoLabel;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String frecuenciaId;
    private Integer idx;
  }
}
