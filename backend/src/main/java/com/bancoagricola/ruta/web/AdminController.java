package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.NotificacionEnviada;
import com.bancoagricola.ruta.dto.Voz;
import com.bancoagricola.ruta.error.ConflictException;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import com.bancoagricola.ruta.service.DashboardService;
import com.bancoagricola.ruta.service.PushService;
import com.bancoagricola.ruta.service.AuditoriaService;
import com.bancoagricola.ruta.service.RutaMotor;
import com.bancoagricola.ruta.service.VozService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operación y demo: correr el motor (con fecha simulada), ver la bitácora de
 * push, métricas del dashboard y ajustes de saldo para provocar un choque.
 * Si ADMIN_KEY está definida, exige la cabecera X-Admin-Key.
 */
@RestController
public class AdminController {
  private final RutaMotor motor;
  private final DashboardService dashboard;
  private final PushService push;
  private final RutaProperties props;
  private final Repositorios.Notificaciones notificaciones;
  private final Repositorios.Clientes clientes;
  private final Repositorios.Cuentas cuentas;
  private final Repositorios.LlamadasVoz llamadasVoz;
  private final Repositorios.PerfilesIngreso perfiles;
  private final VozService voz;
  private final AuditoriaService auditoria;
  private final ObjectMapper json;
  // HTTP/1.1: con HTTP/2 el cliente de Java pide «upgrade» y el servidor de n8n (Node) deja el POST colgado.
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(5)).build();

  public AdminController(RutaMotor motor, DashboardService dashboard, PushService push, RutaProperties props,
                         Repositorios.Notificaciones notificaciones, Repositorios.Clientes clientes, Repositorios.Cuentas cuentas,
                         Repositorios.LlamadasVoz llamadasVoz, Repositorios.PerfilesIngreso perfiles,
                         VozService voz, AuditoriaService auditoria, ObjectMapper json) {
    this.motor = motor;
    this.dashboard = dashboard;
    this.push = push;
    this.props = props;
    this.notificaciones = notificaciones;
    this.clientes = clientes;
    this.cuentas = cuentas;
    this.llamadasVoz = llamadasVoz;
    this.perfiles = perfiles;
    this.voz = voz;
    this.auditoria = auditoria;
    this.json = json;
  }

  private void autorizar(String clave) {
    String esperada = props.adminKey();
    if (esperada != null && !esperada.isBlank() && !esperada.equals(clave)) {
      throw new UnauthorizedException("Falta la cabecera X-Admin-Key.");
    }
  }

  /** Corre el motor diario. `fecha` (AAAA-MM-DD) simula otro día para la demo. */
  @PostMapping({"/admin/ruta/procesar", "/admin/run-push"})
  public Map<String, Object> procesar(@RequestHeader(value = "X-Admin-Key", required = false) String clave,
                                      @RequestParam(required = false) String fecha) {
    autorizar(clave);
    LocalDate dia = fecha == null || fecha.isBlank() ? LocalDate.now() : LocalDate.parse(fecha);
    Map<String, Object> r = new LinkedHashMap<>(motor.procesar(dia));
    r.put("firebase", push.firebaseListo() ? "configurado" : "sin credenciales (envíos SIMULADO; Expo sí envía)");
    return r;
  }

  @GetMapping("/admin/metrics")
  public Map<String, Object> metrics(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    return dashboard.metricas();
  }

  @GetMapping("/admin/notificaciones")
  public List<NotificacionEnviada> notificaciones(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    return notificaciones.findTop50ByOrderByFechaEnvioDesc();
  }

  /** Usuarios de prueba por perfil (todos con clave ruta2026). */
  @GetMapping("/admin/clientes")
  public List<Map<String, Object>> clientes(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    return clientes.findByActivoTrue().stream().map(c -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("usuario", c.getUsername());
      m.put("nombre", c.getDisplayName());
      m.put("perfil", c.getPerfilCrediticio());
      m.put("categoria", c.getCategoria());
      m.put("arquetipo", c.getArchetype());
      m.put("tarjeta", c.getCardTier());
      m.put("conCuenta", !cuentas.findByClienteIdOrderByPrimarySourceDescCreatedAtAsc(c.getId()).isEmpty());
      return m;
    }).toList();
  }

  /** Canal de voz: últimas llamadas y lo que dejó el formulario. Sin transcripción: esa se audita aparte. */
  @GetMapping("/admin/voz/llamadas")
  public List<Map<String, Object>> llamadasVoz(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
    autorizar(clave);
    return llamadasVoz.findTop20ByOrderByCreatedAtDesc().stream().map(l -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", l.getId());
      m.put("clienteId", l.getClienteId());
      m.put("intento", l.getIntento());
      m.put("estado", l.getEstado());
      m.put("resultado", l.getResultado());
      m.put("motivoFin", l.getMotivoFin());
      m.put("diaNuevo", l.getDiaNuevo());
      m.put("diasExtra", l.getDiasExtra());
      m.put("interesExtra", l.getInteresExtra());
      m.put("duracionSeg", l.getDuracionSeg());
      m.put("resumen", l.getResumen());
      perfiles.findById(l.getClienteId()).ifPresent(p -> {
        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("tipoIngreso", p.getTipoIngreso());
        perfil.put("constante", p.getIngresoConstante());
        perfil.put("diasIngreso", p.getDiasIngreso());
        perfil.put("canalPago", p.getCanalPago());
        perfil.put("usaBancaLinea", p.getUsaBancaLinea());
        m.put("perfil", perfil);
      });
      return m;
    }).toList();
  }

  public record DemoVozWeb(String usuario) {}

  /** Demo sin número: registra una llamada web y devuelve las variables que el simulador le pasa a Vapi. */
  @PostMapping("/admin/demo/voz/web")
  public Voz.Programada vozWeb(@RequestHeader(value = "X-Admin-Key", required = false) String clave, @RequestBody DemoVozWeb req) {
    autorizar(clave);
    Cliente c = clientes.findByUsername(req.usuario()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    return voz.programarWeb(c.getId());
  }

  /**
   * Demo sin cuentas: la llamada emulada del navegador le habla a n8n como si fuera Vapi,
   * al mismo webhook y con el mismo secreto, que así no sale del servidor. Solo con VOZ_DEMO.
   */
  @PostMapping("/admin/demo/voz/n8n")
  public ResponseEntity<String> vozN8n(@RequestHeader(value = "X-Admin-Key", required = false) String clave, @RequestBody String mensaje) {
    autorizar(clave);
    if (!props.vozDemo()) throw new ConflictException("El modo demo del canal de voz está apagado (VOZ_DEMO).");
    String url = props.vozN8nWebhook();
    String secreto = props.vozN8nSecreto();
    if (url == null || url.isBlank() || secreto == null || secreto.isBlank()) {
      throw new ConflictException("Faltan VOZ_N8N_WEBHOOK o VOZ_N8N_SECRETO en el backend.");
    }
    HttpRequest pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/json")
        .header("X-Vapi-Secret", secreto)
        .POST(HttpRequest.BodyPublishers.ofString(mensaje))
        .build();
    Map<String, Object> que = describir(mensaje);
    long inicio = System.nanoTime();
    try {
      HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
      long ms = (System.nanoTime() - inicio) / 1_000_000;
      auditoria.registrar(AuditoriaService.N8N, "n8n.mensaje", r.statusCode() >= 400 ? AuditoriaService.AVISO : AuditoriaService.INFO,
          (String) que.get("clienteId"), (String) que.get("llamadaId"),
          "Vapi (emulado) → n8n: " + que.get("que") + " · HTTP " + r.statusCode() + " en " + ms + " ms", que, ms);
      return ResponseEntity.status(r.statusCode()).contentType(MediaType.APPLICATION_JSON).body(r.body());
    } catch (IOException e) {
      auditoria.registrar(AuditoriaService.N8N, "n8n.sin_respuesta", AuditoriaService.ERROR, (String) que.get("clienteId"),
          (String) que.get("llamadaId"), "n8n no respondió en " + url, que, (System.nanoTime() - inicio) / 1_000_000);
      throw new ConflictException("No pude hablar con n8n en " + url + ". ¿Está arriba el contenedor?");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConflictException("Se interrumpió la llamada a n8n.");
    }
  }

  /** Qué llevaba el mensaje de Vapi: tipo, herramientas y a qué llamada pertenece. */
  private Map<String, Object> describir(String mensaje) {
    Map<String, Object> d = new LinkedHashMap<>();
    try {
      JsonNode m = json.readTree(mensaje).path("message");
      List<String> herramientas = new ArrayList<>();
      m.path("toolCallList").forEach(t -> herramientas.add(t.path("name").asText()));
      d.put("tipo", m.path("type").asText(""));
      if (!herramientas.isEmpty()) d.put("herramientas", herramientas);
      d.put("que", herramientas.isEmpty() ? m.path("type").asText("") : String.join(", ", herramientas));
      JsonNode meta = m.path("call").path("metadata");
      d.put("llamadaId", meta.path("llamadaId").asText(null));
      d.put("clienteId", meta.path("clienteId").asText(null));
      if (m.has("endedReason")) d.put("motivoFin", m.path("endedReason").asText());
    } catch (IOException e) {
      d.put("que", "mensaje ilegible");
    }
    return d;
  }

  public record AjusteTelefono(String usuario, String telefono, String municipio, String departamento) {}

  /** Demo del canal de voz: el teléfono del cliente de prueba es el de quien prueba. Nunca se siembra un número real. */
  @PostMapping("/admin/demo/telefono")
  @Transactional
  public Map<String, Object> telefono(@RequestHeader(value = "X-Admin-Key", required = false) String clave, @RequestBody AjusteTelefono req) {
    autorizar(clave);
    Cliente c = clientes.findByUsername(req.usuario()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    String tel = req.telefono() == null ? null : req.telefono().replaceAll("[\\s-]", "");
    if (tel != null && !tel.matches("\\+[0-9]{8,15}")) throw new IllegalArgumentException("El teléfono va en formato internacional, p. ej. +50370000000.");
    c.setTelefono(tel);
    if (req.municipio() != null) c.setMunicipio(req.municipio());
    if (req.departamento() != null) c.setDepartamento(req.departamento());
    clientes.save(c);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("usuario", c.getUsername());
    out.put("clienteId", c.getId());
    out.put("telefono", c.getTelefono());
    out.put("municipio", c.getMunicipio());
    return out;
  }

  public record AjusteSaldo(String usuario, Double saldo) {}

  /** Demo: fija el saldo disponible de la cuenta origen de un cliente (p. ej. para provocar «no alcanzó»). */
  @PostMapping("/admin/demo/saldo")
  @Transactional
  public Map<String, Object> saldo(@RequestHeader(value = "X-Admin-Key", required = false) String clave, @RequestBody AjusteSaldo req) {
    autorizar(clave);
    Cliente c = clientes.findByUsername(req.usuario()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    Cuenta cuenta = cuentas.findByClienteIdOrderByPrimarySourceDescCreatedAtAsc(c.getId()).stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("El cliente no tiene cuenta."));
    cuenta.setBalanceAvailable(BigDecimal.valueOf(req.saldo() == null ? 0 : req.saldo()));
    cuentas.save(cuenta);
    return Map.of("usuario", c.getUsername(), "cuenta", cuenta.getNumberMasked(), "saldo", cuenta.getBalanceAvailable());
  }
}
