package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "CHAT_QUICK_REPLY")
@IdClass(ChatQuickReply.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatQuickReply {
  @Id
  @Column(name = "mensaje_id")
  private String mensajeId;

  @Id
  private Integer idx;

  @Column(name = "opcion_id", nullable = false)
  private String opcionId;

  @Column(nullable = false)
  private String label;

  @Column(name = "next_step")
  private String nextStep;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String mensajeId;
    private Integer idx;
  }
}
