package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Cómo recibe su dinero la persona y cómo prefiere pagar. Es dato del cliente, no del plan: una fila por cliente. */
@Entity
@Table(name = "PERFIL_INGRESO")
@Getter
@Setter
@NoArgsConstructor
public class PerfilIngreso {
  @Id
  @Column(name = "cliente_id")
  private String clienteId;
  @Column(name = "tipo_ingreso", nullable = false)
  private String tipoIngreso;
  @Column(name = "ingreso_constante")
  private Boolean ingresoConstante;
  /** Días del mes en que le entra el dinero, separados por coma (31 = fin de mes). */
  @Column(name = "dias_ingreso")
  private String diasIngreso;
  @Column(name = "canal_pago")
  private String canalPago;
  @Column(name = "usa_banca_linea")
  private Boolean usaBancaLinea;
  @Column(nullable = false)
  private String fuente;
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
