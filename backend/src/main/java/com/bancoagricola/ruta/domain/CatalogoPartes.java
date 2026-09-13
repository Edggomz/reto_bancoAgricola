package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CATALOGO_PARTES")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoPartes {
  @Id
  private Integer parts;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
