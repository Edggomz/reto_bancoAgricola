package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Aviso;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Avisos (vista 11) y su push. REGLA DE CONTENIDO: los avisos confirman lo que
 * ya pasó y nunca recuerdan pagar. Cada aviso del sistema nace aquí: se guarda
 * en AVISO (para verlo en la app) y se intenta enviar como push.
 */
@Service
public class AvisoService {
  public static final String CONFIRM = "confirm";
  public static final String PROGRESS = "progress";
  public static final String PAID = "paid";
  public static final String COMPLETE = "complete";
  public static final String SHOCK = "shock";
  public static final String CORTE = "corte";

  private final Repositorios.Avisos avisos;
  private final PushService push;

  public AvisoService(Repositorios.Avisos avisos, PushService push) {
    this.avisos = avisos;
    this.push = push;
  }

  /** Crea el aviso, lo deja en la bandeja de la app y lo manda por push. */
  @Transactional
  public Aviso emitir(String clienteId, String kind, String titulo, String cuerpo, String target,
                      String creditoId, String tipoPush, LocalDate corteFecha, int diasAntes) {
    Aviso a = new Aviso();
    a.setId("aviso-" + UUID.randomUUID());
    a.setClienteId(clienteId);
    a.setKind(kind);
    a.setTitle(titulo);
    a.setBody(cuerpo);
    a.setTimeLabel(String.format("%d:%02d", LocalDateTime.now().getHour(), LocalDateTime.now().getMinute()));
    a.setDateLabel("HOY");
    a.setReadFlag(false);
    a.setActionable(target != null);
    a.setTarget(target);
    a.setOrden(0);
    a = avisos.saveAndFlush(a);
    push.enviar(a, creditoId, tipoPush, corteFecha, diasAntes);
    return a;
  }

  public Aviso emitir(String clienteId, String kind, String titulo, String cuerpo, String target, String creditoId, String tipoPush) {
    return emitir(clienteId, kind, titulo, cuerpo, target, creditoId, tipoPush, LocalDate.now(), 0);
  }

  @Transactional(readOnly = true)
  public List<App.Aviso> listar(String clienteId) {
    return avisos.findByClienteIdOrderByCreatedAtDesc(clienteId).stream().map(AvisoService::aDto).toList();
  }

  @Transactional
  public void marcarLeidos(String clienteId) {
    List<Aviso> pendientes = avisos.findByClienteIdAndReadFlagFalse(clienteId);
    pendientes.forEach(a -> a.setReadFlag(true));
    avisos.saveAll(pendientes);
  }

  @Transactional
  public void marcarLeido(String clienteId, String avisoId) {
    avisos.findById(avisoId).filter(a -> a.getClienteId().equals(clienteId)).ifPresent(a -> {
      a.setReadFlag(true);
      avisos.save(a);
    });
  }

  public long sinLeer(String clienteId) {
    return avisos.countByClienteIdAndReadFlagFalse(clienteId);
  }

  public static App.Aviso aDto(Aviso a) {
    LocalDateTime creado = a.getCreatedAt() == null ? LocalDateTime.now() : a.getCreatedAt();
    return new App.Aviso(a.getId(), a.getTitle(), a.getBody() == null ? "" : a.getBody(),
        creado.toInstant(ZoneOffset.UTC).toString(), destino(a));
  }

  public static App.Destino destino(Aviso a) {
    if ("record".equals(a.getTarget())) return new App.Destino("record", null);
    if ("advisory-shock".equals(a.getTarget())) return new App.Destino("asesor", a.getId());
    return null;
  }
}
