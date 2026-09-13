package com.bancoagricola.ruta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Registro automático exigido por el reto: transcripción (CHAT_MENSAJE), resultado y fecha acordada. */
@Entity
@Table(name = "CHAT_SESION")
@Getter
@Setter
@NoArgsConstructor
public class ChatSesion {
  public static final String EN_CURSO = "en_curso";
  public static final String ACUERDO = "acuerdo";
  public static final String NEGATIVA = "negativa";
  public static final String SIGUIENTE_PASO = "siguiente_paso";
  public static final String ESCALADO = "escalado";

  @Id
  private String id;
  @Column(name = "cliente_id", nullable = false)
  private String clienteId;
  @Column(name = "aviso_id")
  private String avisoId;
  @Column(nullable = false)
  private String resultado = EN_CURSO;
  /** Qué se le preguntó en el último turno: frecuencia | dia | partes | escalar. */
  private String paso;
  /** Qué quiere resolver: fecha | apartar | automatico. */
  private String intencion;
  @Column(name = "credito_id")
  private String creditoId;
  @Column(name = "partes_elegidas")
  private Integer partesElegidas;
  @Column(name = "oferta_aceptada")
  private String ofertaAceptada;
  @Column(name = "fecha_acordada")
  private LocalDate fechaAcordada;
  @Column(nullable = false)
  private Integer turnos = 0;
  @Column(nullable = false)
  private Integer rechazos = 0;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  public boolean cerrada() {
    return closedAt != null;
  }
}
