package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CATALOGO_DIA_ASESORIA")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoDiaAsesoria {
  @Id
  private String id;

  @Column(nullable = false)
  private String label;

  @Column(name = "when_label", nullable = false)
  private String whenLabel;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
