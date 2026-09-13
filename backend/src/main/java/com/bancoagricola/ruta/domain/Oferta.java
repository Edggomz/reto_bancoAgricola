package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "OFERTA")
@Getter
@Setter
@NoArgsConstructor
public class Oferta {
  @Id
  private String id;

  @Column(nullable = false)
  private String okey;

  @Column(nullable = false)
  private String title;

  private String subtitle;

  @Column(nullable = false)
  private boolean highlighted;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
