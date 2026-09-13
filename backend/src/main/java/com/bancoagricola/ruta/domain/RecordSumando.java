package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "RECORD_SUMANDO")
@Getter
@Setter
@NoArgsConstructor
public class RecordSumando {
  @Id
  private String id;

  @Column(name = "cliente_id", nullable = false)
  private String clienteId;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private Integer orden;
}
