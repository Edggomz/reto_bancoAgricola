package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PARAMETRO_APP")
@Getter
@Setter
@NoArgsConstructor
public class ParametroApp {
  @Id
  private String clave;

  @Column(nullable = false)
  private String valor;

  private String descripcion;
}
