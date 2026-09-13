package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.EventoAuditoria;
import com.bancoagricola.ruta.repository.Repositorios;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoría: qué pasó, cuándo y por qué canal. Es para entender y
 * verificar el sistema, no para operar el negocio: si guardar falla, se avisa en el
 * log y el flujo sigue. Dentro de una transacción el evento se escribe después del
 * commit, así el registro solo muestra lo que de verdad quedó guardado. Nunca guarda
 * claves ni tokens.
 */
@Service
public class AuditoriaService {
  public static final String APP = "app";
  public static final String CHAT = "chat";
  public static final String VOZ = "voz";
  public static final String N8N = "n8n";
  public static final String SISTEMA = "sistema";
  public static final String ADMIN = "admin";

  public static final String INFO = "info";
  public static final String AVISO = "aviso";
  public static final String ERROR = "error";

  private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
  private static final int MAX_DETALLE = 8000;

  private final Repositorios.EventosAuditoria eventos;
  private final ObjectMapper json;
  private final TransactionTemplate aparte;

  public AuditoriaService(Repositorios.EventosAuditoria eventos, ObjectMapper json, PlatformTransactionManager tx) {
    this.eventos = eventos;
    this.json = json;
    this.aparte = new TransactionTemplate(tx);
    this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void registrar(String canal, String tipo, String nivel, String clienteId, String referencia, String resumen,
                        Map<String, ?> detalle, Long duracionMs) {
    EventoAuditoria e;
    try {
      e = new EventoAuditoria();
      e.setId("evt-" + UUID.randomUUID());
      e.setCreatedAt(LocalDateTime.now());
      e.setCanal(canal);
      e.setTipo(recortar(tipo, 48));
      e.setNivel(nivel);
      e.setClienteId(clienteId);
      e.setReferencia(recortar(referencia, 64));
      e.setResumen(recortar(resumen == null ? tipo : resumen, 400));
      e.setDetalle(detalle == null || detalle.isEmpty() ? null : recortar(json.writeValueAsString(detalle), MAX_DETALLE));
      e.setDuracionMs(duracionMs == null ? null : (int) Math.min(duracionMs, 99_999_999L));
    } catch (Exception ex) {
      log.warn("No se pudo armar el evento de auditoría {}: {}", tipo, ex.getMessage());
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          guardar(e);
        }
      });
    } else {
      guardar(e);
    }
  }

  public void info(String canal, String tipo, String clienteId, String referencia, String resumen, Map<String, ?> detalle) {
    registrar(canal, tipo, INFO, clienteId, referencia, resumen, detalle, null);
  }

  private void guardar(EventoAuditoria e) {
    try {
      aparte.executeWithoutResult(estado -> eventos.saveAndFlush(e));
    } catch (Exception ex) {
      log.warn("No se pudo registrar el evento de auditoría {}: {}", e.getTipo(), ex.getMessage());
    }
  }

  private static String recortar(String texto, int max) {
    if (texto == null) return null;
    return texto.length() > max ? texto.substring(0, max - 1) + "…" : texto;
  }
}
