package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "RECORD_HITO")
@IdClass(RecordHito.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class RecordHito {
  @Id
  @Column(name = "cliente_id")
  private String clienteId;

  @Id
  private Integer idx;

  @Column(nullable = false)
  private String label;

  @Column(name = "date_label", nullable = false)
  private String dateLabel;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String clienteId;
    private Integer idx;
  }
}
