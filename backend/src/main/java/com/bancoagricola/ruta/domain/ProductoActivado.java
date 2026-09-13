package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Producto disponible que el cliente ya activó (p. ej. el depósito a plazo): deja de ofrecerse. */
@Entity
@Table(name = "PRODUCTO_ACTIVADO")
@Getter
@Setter
@NoArgsConstructor
public class ProductoActivado {
  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(name = "oferta_id", nullable = false)
  private String ofertaId;
  private String detalle;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
