package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agencias a las que el agente de voz puede mandar a quien prefiere pagar en persona. */
@Entity
@Table(name = "SUCURSAL")
@Getter
@Setter
@NoArgsConstructor
public class Sucursal {
  @Id
  private String id;
  @Column(nullable = false)
  private String nombre;
  @Column(nullable = false)
  private String direccion;
  @Column(nullable = false)
  private String municipio;
  @Column(nullable = false)
  private String departamento;
  /** Horario ya redactado para decirlo en voz («de lunes a viernes de 8:30 a 4:30…»). */
  @Column(nullable = false)
  private String horario;
  @Column(nullable = false)
  private boolean activo;
}
