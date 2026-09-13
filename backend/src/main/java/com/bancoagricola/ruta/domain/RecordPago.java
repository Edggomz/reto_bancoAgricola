package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "RECORD_PAGO")
@Getter
@Setter
@NoArgsConstructor
public class RecordPago {
  @Id
  @Column(name = "cliente_id")
  private String clienteId;

  @Column(name = "streak_months", nullable = false)
  private Integer streakMonths;

  @Column(name = "next_plus_one_label", nullable = false)
  private String nextPlusOneLabel;

  @Column(name = "progress_current", nullable = false)
  private Integer progressCurrent;

  @Column(name = "progress_total", nullable = false)
  private Integer progressTotal;

  @Column(name = "consults_note", nullable = false)
  private String consultsNote;
}
