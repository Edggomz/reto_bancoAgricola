package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.ai.Guardrails;
import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.EventoAuditoria;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import com.bancoagricola.ruta.service.AuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tablero de auditoría (/api/auditoria.html): los eventos recientes, cuántos hubo
 * y si las piezas de la demo están arriba. Si ADMIN_KEY está definida, exige X-Admin-Key.
 */
@RestController
public class AuditoriaController {
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final Repositorios.EventosAuditoria eventos;
  private final Repositorios.Clientes clientes;
  private final AuditoriaService auditoria;
  private final RutaProperties props;
  private final Environment env;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(2)).build();

  public AuditoriaController(Repositorios.EventosAuditoria eventos, Repositorios.Clientes clientes, AuditoriaService auditoria,
                             RutaProperties props, Environment env, ObjectMapper json) {
    this.eventos = eventos;
    this.clientes = clientes;
    this.auditoria = auditoria;
    this.props = props;
    this.env = env;
    this.json = json;
  }

  private void autorizar(String clave) {
    String esperada = props.adminKey();
    if (esperada != null && !esperada.isBlank() && !esperada.equals(clave)) {
      throw new UnauthorizedException("Falta la cabecera X-Admin-Key.");
    }
  }

  @GetMapping("/admin/auditoria")
  public List<Map<String, Object>> eventos(@RequestHeader(value = "X-Admin-Key", required = false) String clave,
                                           @RequestParam(required = false) String canal,
                                           @RequestParam(required = false) String nivel,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String cliente,
                                           @RequestParam(required = false) String desde,
                                           @RequestParam(defaultValue = "200") int limite) {
    autorizar(clave);
    Map<String, String> nombres = clientes.findAll().stream().collect(Collectors.toMap(Cliente::getId, Cliente::getDisplayName));
    String texto = Guardrails.normalizar(q);
    LocalDateTime inicio = vacio(desde) ? null : LocalDateTime.parse(desde.length() > 19 ? desde.substring(0, 19) : desde);
    return eventos.findTop300ByOrderByCreatedAtDesc().stream()
        .filter(e -> vacio(canal) || canal.equals(e.getCanal()))
        .filter(e -> vacio(nivel) || nivel.equals(e.getNivel()))
        .filter(e -> vacio(cliente) || cliente.equals(e.getClienteId()))
        .filter(e -> inicio == null || !e.getCreatedAt().isBefore(inicio))
        .filter(e -> texto.isEmpty() || Guardrails.normalizar(e.getResumen() + " " + e.getTipo() + " "
            + nombres.getOrDefault(e.getClienteId(), "") + " " + (e.getReferencia() == null ? "" : e.getReferencia())).contains(texto))
        .limit(Math.max(1, Math.min(limite, 300)))
        .map(e -> aMapa(e, nombres))
        .toList();
  }

  @GetMapping("/admin/auditoria/resumen")
  public Map<String, Object> resumen(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    LocalDateTime desde = LocalDateTime.now().minusHours(24);
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("eventos", eventos.countByCreatedAtAfter(desde));
    r.put("fechasCambiadas", eventos.countByTipoAndCreatedAtAfter("fecha.cambiada", desde));
    r.put("llamadasTerminadas", eventos.countByTipoAndCreatedAtAfter("voz.llamada.terminada", desde));
    r.put("avisos", eventos.countByNivelAndCreatedAtAfter(AuditoriaService.AVISO, desde));
    r.put("errores", eventos.countByNivelAndCreatedAtAfter(AuditoriaService.ERROR, desde));
    Map<String, Long> porCanal = new LinkedHashMap<>();
    for (Object[] fila : eventos.contarPorCanal(desde)) porCanal.put((String) fila[0], (Long) fila[1]);
    r.put("porCanal", porCanal);
    r.put("ultimo", eventos.findTopByOrderByCreatedAtDesc().map(e -> e.getCreatedAt().toString()).orElse(null));
    r.put("salud", salud());
    r.put("verificaciones", verificaciones(desde));
    return r;
  }

  /** Lista de «¿esto funciona?»: cada pieza con cuántas veces pasó en 24 h, la última vez y cómo probarla. */
  private List<Map<String, Object>> verificaciones(LocalDateTime desde) {
    String[][] piezas = {
        {"sesion.ingreso", "Alguien entró a la app", "Entra a la app con edgar.gomez y la clave ruta2026."},
        {"dispositivo.registrado", "Un teléfono quedó listo para push", "Abre la app en el emulador o el celular."},
        {"fecha.cambiada", "Se movió una fecha de cobro (app o voz)", "En la app: Cambiar fecha de cobro. O haz la llamada emulada."},
        {"apartado.activado", "Se activó un apartado de cuota", "En la app: Apartar mi cuota, después de cambiar la fecha."},
        {"chat.iniciado", "Alguien abrió el asesor", "En la app: botón Asesor."},
        {"voz.formulario", "La llamada guardó el formulario", "Haz la llamada emulada hasta el final."},
        {"n8n.mensaje", "n8n respondió a la llamada", "Haz la llamada emulada: cada paso pasa por n8n."},
        {"aviso.emitido", "El sistema emitió un aviso", "Cambia la fecha o corre el motor diario en el dashboard."},
        {"push.", "Se intentó un push", "Corre el motor diario con un cliente cerca de su corte."},
        {"motor.diario", "Corrió el motor diario", "Dashboard de gestión: Correr motor hoy."},
    };
    List<Map<String, Object>> out = new ArrayList<>();
    for (String[] p : piezas) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("tipo", p[0]);
      m.put("nombre", p[1]);
      m.put("veces", eventos.countByTipoStartingWithAndCreatedAtAfter(p[0], desde));
      m.put("ultima", eventos.findTopByTipoStartingWithOrderByCreatedAtDesc(p[0]).map(e -> e.getCreatedAt().toString()).orElse(null));
      m.put("comoProbar", p[2]);
      if ("n8n.mensaje".equals(p[0])) {
        Double ms = eventos.promedioMs(p[0], desde);
        m.put("promedioMs", ms == null ? null : Math.round(ms));
      }
      out.add(m);
    }
    return out;
  }

  @DeleteMapping("/admin/auditoria")
  public ResponseEntity<Void> vaciar(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    eventos.deleteAllInBatch();
    auditoria.info(AuditoriaService.ADMIN, "auditoria.vaciada", null, null, "Se vació el registro de auditoría", null);
    return ResponseEntity.noContent().build();
  }

  private Map<String, Object> aMapa(EventoAuditoria e, Map<String, String> nombres) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("fecha", e.getCreatedAt().toString());
    m.put("hora", e.getCreatedAt().format(HORA));
    m.put("canal", e.getCanal());
    m.put("tipo", e.getTipo());
    m.put("nivel", e.getNivel());
    m.put("clienteId", e.getClienteId());
    m.put("cliente", e.getClienteId() == null ? null : nombres.getOrDefault(e.getClienteId(), e.getClienteId()));
    m.put("referencia", e.getReferencia());
    m.put("resumen", e.getResumen());
    m.put("duracionMs", e.getDuracionMs());
    Object detalle = e.getDetalle();
    if (e.getDetalle() != null) {
      try {
        detalle = json.readTree(e.getDetalle());
      } catch (Exception ignorada) {
        detalle = e.getDetalle();
      }
    }
    m.put("detalle", detalle);
    return m;
  }

  /** ok, aviso o falla por pieza, siempre con texto: el color no carga el significado solo. */
  private List<Map<String, Object>> salud() {
    List<Map<String, Object>> s = new ArrayList<>();
    String[] perfiles = env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles();
    s.add(chequeo("Backend", "ok", "perfil " + String.join(", ", perfiles)));
    try {
      s.add(chequeo("Base de datos", "ok", clientes.count() + " clientes"));
    } catch (Exception e) {
      s.add(chequeo("Base de datos", "falla", "no responde"));
    }
    String webhook = props.vozN8nWebhook();
    if (vacio(webhook)) {
      s.add(chequeo("n8n", "aviso", "sin VOZ_N8N_WEBHOOK"));
    } else {
      String base = webhook.contains("/webhook") ? webhook.substring(0, webhook.indexOf("/webhook")) : webhook;
      try {
        HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(base + "/healthz")).timeout(Duration.ofSeconds(2)).GET().build(),
            HttpResponse.BodyHandlers.discarding());
        s.add(chequeo("n8n", r.statusCode() == 200 ? "ok" : "falla", base + " · HTTP " + r.statusCode()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        s.add(chequeo("n8n", "falla", base + " no responde"));
      } catch (Exception e) {
        s.add(chequeo("n8n", "falla", base + " no responde"));
      }
    }
    boolean conLlave = !vacio(props.vozKey());
    s.add(chequeo("Canal de voz", conLlave ? "ok" : "falla",
        conLlave ? (props.vozDemo() ? "llave lista · modo demo" : "llave lista") : "falta VOZ_KEY"));
    s.add(chequeo("Llave de admin", vacio(props.adminKey()) ? "aviso" : "ok",
        vacio(props.adminKey()) ? "sin ADMIN_KEY: solo para uso local" : "protegido"));
    return s;
  }

  private static Map<String, Object> chequeo(String nombre, String estado, String detalle) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("nombre", nombre);
    m.put("estado", estado);
    m.put("detalle", detalle);
    return m;
  }

  private static boolean vacio(String s) {
    return s == null || s.isBlank();
  }
}
