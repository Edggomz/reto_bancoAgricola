package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/** «¿Qué día te pagan?»: cada frecuencia define sus días de pago y cuántas partes permite. */
@Entity
@Table(name = "CATALOGO_FRECUENCIA")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoFrecuencia {
  public static final String MENSUAL = "mensual";
  public static final String SEMANAL = "semanal";
  public static final String VARIABLE = "variable";

  @Id
  private String id;
  @Column(nullable = false)
  private String slug;
  @Column(nullable = false)
  private String label;
  private String descripcion;
  @Column(nullable = false)
  private String tipo;
  @Column(name = "offset_min", nullable = false)
  private Integer offsetMin;
  @Column(name = "offset_max", nullable = false)
  private Integer offsetMax;
  @Column(name = "partes_permitidas", nullable = false)
  private String partesPermitidas;
  @Column(name = "partes_sugeridas", nullable = false)
  private Integer partesSugeridas;
  @Column(name = "nota_partes")
  private String notaPartes;
  @Column(nullable = false)
  private Integer orden;
  @Column(nullable = false)
  private boolean activo;

  public boolean isSemanal() {
    return SEMANAL.equals(tipo);
  }

  public boolean isVariable() {
    return VARIABLE.equals(tipo);
  }

  public List<Integer> partes() {
    return Arrays.stream(partesPermitidas.split(",")).map(String::trim).filter(s -> !s.isEmpty())
        .map(Integer::parseInt).toList();
  }
}
