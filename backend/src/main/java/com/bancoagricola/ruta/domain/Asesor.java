package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ASESOR")
@Getter
@Setter
@NoArgsConstructor
public class Asesor {
  @Id
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(name = "since_label")
  private String sinceLabel;

  @Column(nullable = false)
  private String agency;

  private String initials;

  @Column(name = "avatar_color")
  private String avatarColor;

  @Column(nullable = false)
  private boolean activo;

  public String primerNombre() {
    int espacio = name.indexOf(' ');
    return espacio > 0 ? name.substring(0, espacio) : name;
  }
}
