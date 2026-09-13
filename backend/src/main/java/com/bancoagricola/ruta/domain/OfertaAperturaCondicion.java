package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "OFERTA_APERTURA_CONDICION")
@IdClass(OfertaAperturaCondicion.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class OfertaAperturaCondicion {
  @Id
  @Column(name = "oferta_id")
  private String ofertaId;

  @Id
  private Integer idx;

  @Column(nullable = false)
  private String texto;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String ofertaId;
    private Integer idx;
  }
}
