package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ASESORIA_TEMA")
@Getter
@Setter
@NoArgsConstructor
public class AsesoriaTema {
  @Id
  private String id;

  @Column(name = "producto_tipo", nullable = false)
  private String productoTipo;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
