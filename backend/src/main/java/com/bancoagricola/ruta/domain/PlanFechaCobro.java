package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "PLAN_FECHA_COBRO")
@Getter
@Setter
@NoArgsConstructor
public class PlanFechaCobro {
  public static final String CANAL_APP = "app";
  public static final String CANAL_VOZ = "voz";

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
  /** Días que se corre la primera cuota con la fecha nueva (0 si se adelanta). */
  @Column(name = "dias_extra", nullable = false)
  private Integer diasExtra = 0;
  /** Interés de esos días. Se cobra una vez y solo si la persona lo aceptó. */
  @Column(name = "interes_extra", nullable = false)
  private BigDecimal interesExtra = BigDecimal.ZERO;
  @Column(name = "acepto_interes", nullable = false)
  private boolean aceptoInteres;
  @Column(nullable = false)
  private String canal = CANAL_APP;
  /** Antes de este día no se puede volver a cambiar la fecha. */
  @Column(name = "bloqueado_hasta")
  private LocalDate bloqueadoHasta;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
