package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUTOPAGO")
@Getter
@Setter
@NoArgsConstructor
public class Autopago {
  @Id
  private String id;

  @Column(name = "credito_id", nullable = false)
  private String creditoId;

  @Column(name = "account_id", nullable = false)
  private String accountId;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
