package com.bancoagricola.ruta.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CATALOGO_CHAT_OPCION")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoChatOpcion {
  @Id
  private String id;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private String accion;

  @Column(nullable = false)
  private String contexto;

  @Column(nullable = false)
  private Integer orden;

  @Column(nullable = false)
  private boolean activo;
}
