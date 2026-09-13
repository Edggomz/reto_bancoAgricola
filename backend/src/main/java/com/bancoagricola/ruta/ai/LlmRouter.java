package com.bancoagricola.ruta.ai;

import com.bancoagricola.ruta.config.AiProperties;
import com.bancoagricola.ruta.domain.IaLlamada;
import com.bancoagricola.ruta.repository.Repositorios;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enruta las llamadas al modelo con FALLBACK automático (Plan B: primario
 * Gemini Flash, secundario Groq gpt-oss-120b). HTTP directo (java.net.http)
 * para no depender de una versión concreta de langchain4j; la interfaz es la
 * misma y puede sustituirse sin tocar los servicios.
 *
 * Si un proveedor no tiene API key se salta; si ninguno responde devuelve
 * vacío y el servicio usa su guion determinista (la demo corre sin llaves).
 * Cada intento queda en IA_LLAMADA (proveedor, latencia, éxito, fallback) para
 * el dashboard.
 */
@Component
public class LlmRouter {
  private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

  /** Una llamada: `rapido` usa el modelo flash-lite (clasificar/sugerir); `json` pide un objeto JSON. */
  public record Solicitud(String servicio, String system, String user, boolean json, boolean rapido, int maxTokens,
                          double temperatura, String sesionId) {}

  public record Respuesta(String texto, String proveedor, String modelo, long latenciaMs, boolean fallback) {}

  private final AiProperties props;
  private final ObjectMapper mapper;
  private final Repositorios.LlamadasIa llamadas;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
  private final AtomicLong calls = new AtomicLong();
  private final AtomicLong fallbacks = new AtomicLong();
  private final AtomicLong sinModelo = new AtomicLong();

  public LlmRouter(AiProperties props, ObjectMapper mapper, Repositorios.LlamadasIa llamadas) {
    this.props = props;
    this.mapper = mapper;
    this.llamadas = llamadas;
  }

  public boolean disponible() {
    return props.getGemini().enabled() || props.getGroq().enabled() || props.getOpenai().enabled();
  }

  public Optional<Respuesta> generar(Solicitud s) {
    calls.incrementAndGet();
    List<String> orden = new ArrayList<>();
    if (props.getPrimary() != null) orden.add(props.getPrimary().toLowerCase());
    if (props.getSecondary() != null && !orden.contains(props.getSecondary().toLowerCase())) orden.add(props.getSecondary().toLowerCase());
    boolean fallback = false;
    for (String nombre : orden) {
      Optional<Respuesta> r = intentar(nombre, s, fallback);
      if (r.isPresent()) {
        if (fallback) {
          fallbacks.incrementAndGet();
          log.warn("IA fallback: primario no respondió, sirvió '{}'", nombre);
        }
        return r;
      }
      fallback = true;
    }
    sinModelo.incrementAndGet();
    log.warn("IA sin respuesta ({}): se usa el guion determinista.", s.servicio());
    return Optional.empty();
  }

  private Optional<Respuesta> intentar(String nombre, Solicitud s, boolean esFallback) {
    AiProperties.Provider p = proveedor(nombre);
    if (p == null || !p.enabled()) return Optional.empty();
    String modelo = "gemini".equals(nombre) ? (s.rapido() ? props.getGemini().getModelFast() : props.getGemini().getModel()) : p.getModel();
    long t0 = System.currentTimeMillis();
    try {
      String texto = "gemini".equals(nombre) ? gemini(props.getGemini(), modelo, s) : openAiCompatible(p, modelo, s);
      long latencia = System.currentTimeMillis() - t0;
      if (texto == null || texto.isBlank()) throw new IllegalStateException("respuesta vacía");
      registrar(s.servicio(), nombre, modelo, true, esFallback, latencia, null, s.sesionId());
      return Optional.of(new Respuesta(texto.trim(), nombre, modelo, latencia, esFallback));
    } catch (Exception e) {
      long latencia = System.currentTimeMillis() - t0;
      log.warn("Proveedor '{}' ({}) falló: {}", nombre, modelo, e.toString());
      registrar(s.servicio(), nombre, modelo, false, esFallback, latencia, recortar(e.toString()), s.sesionId());
      return Optional.empty();
    }
  }

  private AiProperties.Provider proveedor(String nombre) {
    return switch (nombre) {
      case "gemini" -> props.getGemini();
      case "groq" -> props.getGroq();
      case "openai" -> props.getOpenai();
      default -> null;
    };
  }

  // Gemini (Google AI) — v1beta generateContent, llave en cabecera x-goog-api-key.
  private String gemini(AiProperties.Gemini g, String modelo, Solicitud s) throws Exception {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("temperature", s.temperatura());
    // El modelo principal razona antes de responder y esos tokens cuentan en el límite.
    config.put("maxOutputTokens", s.rapido() ? Math.max(256, s.maxTokens()) : Math.max(g.getMaxOutputTokens(), s.maxTokens()));
    if (s.json()) config.put("responseMimeType", "application/json");
    if (!s.rapido() && g.getThinkingLevel() != null && !g.getThinkingLevel().isBlank()) {
      config.put("thinkingConfig", Map.of("thinkingLevel", g.getThinkingLevel()));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", s.system()))));
    body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", s.user())))));
    body.put("generationConfig", config);
    // Las llamadas rápidas (clasificar, sugerir) esperan poco: si Gemini está saturado, Groq responde en <1 s.
    long timeout = s.rapido() ? Math.min(g.getTimeoutMs(), 4500) : g.getTimeoutMs();
    HttpRequest req = HttpRequest.newBuilder(URI.create(g.getBaseUrl() + "/models/" + modelo + ":generateContent"))
        .timeout(Duration.ofMillis(timeout))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", g.getApiKey())
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) throw new IllegalStateException("Gemini HTTP " + res.statusCode() + " " + recortar(res.body()));
    JsonNode root = mapper.readTree(res.body());
    JsonNode candidato = root.path("candidates").path(0);
    StringBuilder texto = new StringBuilder();
    for (JsonNode parte : candidato.path("content").path("parts")) {
      if (!parte.path("thought").asBoolean(false)) texto.append(parte.path("text").asText(""));
    }
    if (texto.length() == 0) throw new IllegalStateException("sin texto (finishReason=" + candidato.path("finishReason").asText() + ")");
    return texto.toString();
  }

  // Groq / OpenAI — /chat/completions (formato OpenAI).
  private String openAiCompatible(AiProperties.Provider p, String modelo, Solicitud s) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", modelo);
    body.put("temperature", s.temperatura());
    body.put("max_tokens", s.maxTokens() + 400);
    body.put("messages", List.of(Map.of("role", "system", "content", s.system()), Map.of("role", "user", "content", s.user())));
    if (p.getReasoningEffort() != null && !p.getReasoningEffort().isBlank() && modelo.contains("gpt-oss")) {
      body.put("reasoning_effort", p.getReasoningEffort());
    }
    HttpRequest req = HttpRequest.newBuilder(URI.create(p.getBaseUrl() + "/chat/completions"))
        .timeout(Duration.ofMillis(p.getTimeoutMs()))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + p.getApiKey())
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) throw new IllegalStateException("LLM HTTP " + res.statusCode() + " " + recortar(res.body()));
    return mapper.readTree(res.body()).path("choices").path(0).path("message").path("content").asText(null);
  }

  /**
   * Llama Prompt Guard 2 (Groq): probabilidad de que el texto sea un intento de
   * jailbreak / inyección. Vacío si el guard está apagado o no hay llave de Groq.
   */
  public Optional<Double> probabilidadInyeccion(String texto, String sesionId) {
    AiProperties.Guard guard = props.getGuard();
    AiProperties.Provider groq = props.getGroq();
    if (!guard.isEnabled() || !groq.enabled() || texto == null || texto.isBlank()) return Optional.empty();
    long t0 = System.currentTimeMillis();
    try {
      Map<String, Object> body = Map.of("model", guard.getModel(), "messages", List.of(Map.of("role", "user", "content", texto)));
      HttpRequest req = HttpRequest.newBuilder(URI.create(groq.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofMillis(guard.getTimeoutMs()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + groq.getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() / 100 != 2) throw new IllegalStateException("Guard HTTP " + res.statusCode());
      String contenido = mapper.readTree(res.body()).path("choices").path(0).path("message").path("content").asText("").trim();
      double prob = Double.parseDouble(contenido);
      registrar("guard", "groq", guard.getModel(), true, false, System.currentTimeMillis() - t0,
          null, sesionId, prob >= guard.getThreshold() ? "inyeccion:" + prob : null);
      return Optional.of(prob);
    } catch (Exception e) {
      registrar("guard", "groq", guard.getModel(), false, false, System.currentTimeMillis() - t0, recortar(e.toString()), sesionId, null);
      return Optional.empty();
    }
  }

  /** Extrae el primer objeto JSON de una respuesta (tolera texto o cercas ``` alrededor). */
  public Optional<JsonNode> json(String raw) {
    if (raw == null) return Optional.empty();
    String s = raw.replace("```json", "").replace("```", "").trim();
    int a = s.indexOf('{');
    int b = s.lastIndexOf('}');
    if (a < 0 || b <= a) return Optional.empty();
    try {
      return Optional.of(mapper.readTree(s.substring(a, b + 1)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public Map<String, Object> metricas() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("calls", calls.get());
    m.put("fallbacks", fallbacks.get());
    m.put("withoutModel", sinModelo.get());
    m.put("primary", props.getPrimary());
    m.put("secondary", props.getSecondary());
    m.put("providersEnabled", Map.of("gemini", props.getGemini().enabled(), "groq", props.getGroq().enabled(), "openai", props.getOpenai().enabled()));
    m.put("guardEnabled", props.getGuard().isEnabled() && props.getGroq().enabled());
    try {
      m.put("registradas", Map.of("chat", llamadas.countByServicio("chat"), "form", llamadas.countByServicio("form"),
          "sugerencia", llamadas.countByServicio("sugerencia"), "guard", llamadas.countByServicio("guard")));
      m.put("porProveedor", Map.of("gemini", llamadas.countByProveedor("gemini"), "groq", llamadas.countByProveedor("groq")));
      m.put("fallos", llamadas.countByExitoFalse());
      m.put("fallbacksRegistrados", llamadas.countByFallbackTrue());
      m.put("guardrailsAplicados", llamadas.countByGuardrailIsNotNull());
      Double lat = llamadas.latenciaPromedio();
      m.put("latenciaPromedioMs", lat == null ? 0 : Math.round(lat));
    } catch (Exception e) {
      log.debug("Métricas de IA no disponibles: {}", e.toString());
    }
    return m;
  }

  private void registrar(String servicio, String proveedor, String modelo, boolean exito, boolean fallback, long latencia,
                         String error, String sesionId) {
    registrar(servicio, proveedor, modelo, exito, fallback, latencia, error, sesionId, null);
  }

  /** Bitácora en IA_LLAMADA; nunca rompe la llamada de negocio. */
  public void registrar(String servicio, String proveedor, String modelo, boolean exito, boolean fallback, long latencia,
                        String error, String sesionId, String guardrail) {
    try {
      IaLlamada l = new IaLlamada();
      l.setId("ia-" + UUID.randomUUID());
      l.setServicio(servicio);
      l.setProveedor(proveedor);
      l.setModelo(modelo);
      l.setExito(exito);
      l.setFallback(fallback);
      l.setLatenciaMs(latencia);
      l.setError(error);
      l.setGuardrail(guardrail);
      l.setSesionId(sesionId);
      llamadas.save(l);
    } catch (Exception e) {
      log.debug("No se pudo registrar la llamada de IA: {}", e.toString());
    }
  }

  private static String recortar(String s) {
    return s == null ? null : s.length() > 400 ? s.substring(0, 400) : s;
  }
}
