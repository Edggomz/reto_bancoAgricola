package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "PLAN_FECHA_COBRO")
@Getter
@Setter
@NoArgsConstructor
public class PlanFechaCobro {
  @Id
  @Column(name = "credito_id")
  private String creditoId;
  @Column(name = "new_day", nullable = false)
  private Integer newDay;
  @Column(name = "effective_from_label", nullable = false)
  private String effectiveFromLabel;
  @Column(name = "effective_from")
  private LocalDate effectiveFrom;
  @Column(name = "amount_unchanged", nullable = false)
  private boolean amountUnchanged = true;
  @Column(name = "term_unchanged", nullable = false)
  private boolean termUnchanged = true;
  @Column(name = "frecuencia_id", nullable = false)
  private String frecuenciaId;
  @Column(name = "opcion_id", nullable = false)
  private String opcionId;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
