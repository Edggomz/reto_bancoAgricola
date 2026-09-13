package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "CLIENTE")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {
  @Id
  private String id;
  @Column(nullable = false)
  private String username;
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;
  @Column(name = "first_name", nullable = false)
  private String firstName;
  @Column(name = "display_name", nullable = false)
  private String displayName;
  @Column(nullable = false)
  private String initials;
  @Column(name = "avatar_color", nullable = false)
  private String avatarColor;
  @Column(nullable = false)
  private String voice;
  @Column(name = "card_tier", nullable = false)
  private String cardTier;
  @Column(nullable = false)
  private String archetype;
  @Column(name = "first_time_at_risk", nullable = false)
  private boolean firstTimeAtRisk;
  @Column(name = "perfil_crediticio", nullable = false)
  private String perfilCrediticio;
  @Column(nullable = false)
  private String categoria;
  @Column(name = "asesor_id", nullable = false)
  private String asesorId;
  /** Respuesta a «¿Qué día te pagan?» (id de CATALOGO_FRECUENCIA). NULL = aún no respondió. */
  @Column(name = "frecuencia_pago")
  private String frecuenciaPago;
  @Column(name = "fecha_alta", nullable = false)
  private LocalDate fechaAlta;
  @Column(nullable = false)
  private boolean activo;
}
