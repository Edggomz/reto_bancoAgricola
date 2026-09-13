package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "CHAT_MENSAJE")
@Getter
@Setter
@NoArgsConstructor
public class ChatMensaje {
  @Id
  private String id;

  @Column(name = "sesion_id", nullable = false)
  private String sesionId;

  @Column(nullable = false)
  private String rol;

  @Lob
  @Column(nullable = false)
  private String texto;

  @Column(nullable = false)
  private String origen;

  private String proveedor;

  @Column(nullable = false)
  private Integer orden;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
