package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "CITA")
@Getter
@Setter
@NoArgsConstructor
public class Cita {
  public static final String AGENDADA = "scheduled";
  public static final String CANCELADA = "cancelled";

  @Id
  private String id;

  @Column(name = "cliente_id", nullable = false)
  private String clienteId;

  @Column(name = "asesor_id", nullable = false)
  private String asesorId;

  @Column(name = "tema_id", nullable = false)
  private String temaId;

  @Column(name = "producto_id")
  private String productoId;

  @Column(name = "dia_id", nullable = false)
  private String diaId;

  @Column(name = "hora_id", nullable = false)
  private String horaId;

  @Column(name = "with_label", nullable = false)
  private String withLabel;

  @Column(name = "when_label", nullable = false)
  private String whenLabel;

  @Column(name = "where_label", nullable = false)
  private String whereLabel;

  @Column(name = "about_label", nullable = false)
  private String aboutLabel;

  @Column(name = "confirmation_note", nullable = false)
  private String confirmationNote;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String source;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
