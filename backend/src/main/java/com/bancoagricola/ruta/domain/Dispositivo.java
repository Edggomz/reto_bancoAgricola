package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "DISPOSITIVO")
@Getter
@Setter
@NoArgsConstructor
public class Dispositivo {
  @Id
  private String id;

  @Column(name = "cliente_id", nullable = false)
  private String clienteId;

  @Column(name = "push_token", nullable = false)
  private String pushToken;

  @Column(nullable = false)
  private String platform;

  @Column(nullable = false)
  private boolean activo;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
