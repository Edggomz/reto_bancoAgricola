package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CATALOGO_HORA_ASESORIA")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoHoraAsesoria {
  @Id
  private String id;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
