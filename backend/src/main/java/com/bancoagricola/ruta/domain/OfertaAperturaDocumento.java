package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "OFERTA_APERTURA_DOCUMENTO")
@Getter
@Setter
@NoArgsConstructor
public class OfertaAperturaDocumento {
  @Id
  private String id;

  @Column(name = "oferta_id", nullable = false)
  private String ofertaId;

  @Column(nullable = false)
  private String titulo;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private Integer orden;
}
