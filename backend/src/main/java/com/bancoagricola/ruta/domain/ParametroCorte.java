package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PARAMETRO_CORTE")
@Getter
@Setter
@NoArgsConstructor
public class ParametroCorte {
  @Id
  @Column(name = "dia_corte")
  private Integer diaCorte;

  private String descripcion;

  @Column(nullable = false)
  private boolean activo;
}
