package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Aviso;
import com.bancoagricola.ruta.domain.Dispositivo;
import com.bancoagricola.ruta.domain.NotificacionEnviada;
import com.bancoagricola.ruta.repository.Repositorios;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envío de push y su bitácora (NOTIFICACION_ENVIADA).
 *
 * - Token de Expo (ExponentPushToken[...]) -> Expo Push Service, sin credenciales.
 * - Cualquier otro token -> Firebase Cloud Messaging, si hay service account
 *   (GOOGLE_APPLICATION_CREDENTIALS o FIREBASE_CREDENTIALS_B64).
 * - Sin credenciales o sin dispositivo -> queda registrado como SIMULADO y la
 *   demo sigue: el aviso igual aparece en la vista 11 de la app.
 *
 * La carga `data` es la que lee final/src/avisos/push.ts:
 *   { id, fecha (ISO), destino: { vista: 'record' } | { vista: 'asesor', aviso } | null }
 */
@Service
public class PushService {
  private static final Logger log = LoggerFactory.getLogger(PushService.class);

  private final Repositorios.Dispositivos dispositivos;
  private final Repositorios.Notificaciones notificaciones;
  private final RutaProperties props;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
  private FirebaseMessaging firebase;

  public PushService(Repositorios.Dispositivos dispositivos, Repositorios.Notificaciones notificaciones,
                     RutaProperties props, ObjectMapper mapper) {
    this.dispositivos = dispositivos;
    this.notificaciones = notificaciones;
    this.props = props;
    this.mapper = mapper;
    this.firebase = iniciarFirebase();
  }

  public boolean firebaseListo() {
    return firebase != null;
  }

  private FirebaseMessaging iniciarFirebase() {
    try {
      RutaProperties.Firebase cfg = props.firebase();
      InputStream creds = null;
      if (cfg != null && cfg.credentialsB64() != null && !cfg.credentialsB64().isBlank()) {
        creds = new ByteArrayInputStream(Base64.getDecoder().decode(cfg.credentialsB64().trim()));
      } else if (cfg != null && cfg.credentialsPath() != null && !cfg.credentialsPath().isBlank()) {
        creds = new FileInputStream(cfg.credentialsPath().trim());
      }
      if (creds == null) {
        log.info("Firebase sin credenciales: los push FCM quedan como SIMULADO (Expo sí se envía).");
        return null;
      }
      FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(creds)).build();
      FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
      log.info("Firebase listo para enviar push (proyecto {}).", app.getOptions().getProjectId());
      return FirebaseMessaging.getInstance(app);
    } catch (Exception e) {
      log.warn("No se pudo iniciar Firebase ({}); los push FCM quedan como SIMULADO.", e.toString());
      return null;
    }
  }

  /** Envía el aviso a todos los dispositivos del cliente y deja constancia de cada intento. */
  public List<NotificacionEnviada> enviar(Aviso aviso, String creditoId, String tipo, LocalDate corteFecha, int diasAntes) {
    List<Dispositivo> destinos = dispositivos.findByClienteIdAndActivoTrue(aviso.getClienteId());
    List<NotificacionEnviada> out = new ArrayList<>();
    if (destinos.isEmpty()) {
      out.add(registrar(aviso, creditoId, null, "simulado", tipo, corteFecha, diasAntes, NotificacionEnviada.SIMULADO,
          "Sin dispositivo registrado"));
      return out;
    }
    Map<String, Object> data = carga(aviso);
    for (Dispositivo d : destinos) {
      String token = d.getPushToken() == null ? "" : d.getPushToken();
      try {
        if (token.startsWith("ExponentPushToken")) {
          enviarExpo(token, aviso, data);
          out.add(registrar(aviso, creditoId, d.getId(), "expo", tipo, corteFecha, diasAntes, NotificacionEnviada.ENVIADO, null));
        } else if (firebase != null) {
          enviarFcm(token, aviso, data);
          out.add(registrar(aviso, creditoId, d.getId(), "fcm", tipo, corteFecha, diasAntes, NotificacionEnviada.ENVIADO, null));
        } else {
          out.add(registrar(aviso, creditoId, d.getId(), "simulado", tipo, corteFecha, diasAntes, NotificacionEnviada.SIMULADO,
              "Sin credenciales de Firebase"));
        }
      } catch (Exception e) {
        log.warn("Push a {} falló: {}", d.getId(), e.toString());
        out.add(registrar(aviso, creditoId, d.getId(), token.startsWith("ExponentPushToken") ? "expo" : "fcm", tipo,
            corteFecha, diasAntes, NotificacionEnviada.ERROR, recortar(e.toString())));
      }
    }
    return out;
  }

  private Map<String, Object> carga(Aviso aviso) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", aviso.getId());
    data.put("fecha", aviso.getCreatedAt() == null ? LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toString()
        : aviso.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
    Map<String, Object> destino = null;
    if ("record".equals(aviso.getTarget())) destino = Map.of("vista", "record");
    if ("advisory-shock".equals(aviso.getTarget())) destino = Map.of("vista", "asesor", "aviso", aviso.getId());
    data.put("destino", destino);
    return data;
  }

  private void enviarExpo(String token, Aviso aviso, Map<String, Object> data) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("to", token);
    body.put("title", aviso.getTitle());
    body.put("body", aviso.getBody() == null ? "" : aviso.getBody());
    body.put("data", data);
    body.put("sound", "default");
    body.put("channelId", "mi-ruta");
    HttpRequest req = HttpRequest.newBuilder(URI.create(props.push().expoUrl()))
        .timeout(Duration.ofSeconds(8))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) throw new IllegalStateException("Expo HTTP " + res.statusCode());
    JsonNode root = mapper.readTree(res.body());
    JsonNode ticket = root.path("data").isArray() ? root.path("data").path(0) : root.path("data");
    if (!"ok".equals(ticket.path("status").asText())) {
      throw new IllegalStateException("Expo: " + ticket.path("message").asText("ticket no ok"));
    }
  }

  private void enviarFcm(String token, Aviso aviso, Map<String, Object> data) throws Exception {
    Message.Builder msg = Message.builder()
        .setToken(token)
        .setNotification(Notification.builder().setTitle(aviso.getTitle()).setBody(aviso.getBody() == null ? "" : aviso.getBody()).build());
    for (Map.Entry<String, Object> e : data.entrySet()) {
      Object v = e.getValue();
      msg.putData(e.getKey(), v == null ? "" : v instanceof String s ? s : mapper.writeValueAsString(v));
    }
    firebase.send(msg.build());
  }

  private NotificacionEnviada registrar(Aviso aviso, String creditoId, String dispositivoId, String canal, String tipo,
                                        LocalDate corteFecha, int diasAntes, String estado, String detalle) {
    NotificacionEnviada n = new NotificacionEnviada();
    n.setId("notif-" + UUID.randomUUID());
    n.setClienteId(aviso.getClienteId());
    n.setCreditoId(creditoId);
    n.setDispositivoId(dispositivoId);
    n.setAvisoId(aviso.getId());
    n.setCanal(canal);
    n.setTipo(tipo);
    n.setTitulo(aviso.getTitle());
    n.setCuerpo(aviso.getBody() == null ? "" : aviso.getBody());
    n.setCorteFecha(corteFecha == null ? LocalDate.now() : corteFecha);
    n.setDiasAntesCorte(Math.max(0, diasAntes));
    n.setEstado(estado);
    n.setDetalleError(detalle);
    return notificaciones.save(n);
  }

  private static String recortar(String s) {
    return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
  }
}
