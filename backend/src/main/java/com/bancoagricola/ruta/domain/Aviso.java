package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "AVISO")
@Getter
@Setter
@NoArgsConstructor
public class Aviso {
  @Id
  private String id;

  @Column(name = "cliente_id", nullable = false)
  private String clienteId;

  @Column(nullable = false)
  private String kind;

  @Column(nullable = false)
  private String title;

  private String body;

  @Column(name = "time_label")
  private String timeLabel;

  @Column(name = "date_label", nullable = false)
  private String dateLabel;

  @Column(name = "read_flag", nullable = false)
  private boolean readFlag;

  @Column(nullable = false)
  private boolean actionable;

  private String target;

  @Column(nullable = false)
  private Integer orden;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
