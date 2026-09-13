package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "TRANSACCION")
@Getter
@Setter
@NoArgsConstructor
public class Transaccion {
  @Id
  private String id;

  @Column(name = "credito_id", nullable = false)
  private String creditoId;

  @Column(name = "numero_producto", nullable = false)
  private String numeroProducto;

  @Column(nullable = false)
  private LocalDate fecha;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 1)
  private String tipo;

  @Column(nullable = false)
  private BigDecimal monto;

  private String descripcion;

  @Column(name = "frontera_contada")
  private String fronteraContada;
}
