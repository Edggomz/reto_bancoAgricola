package com.bancoagricola.ruta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de IA por entorno (prefijo `ai.`), ver application.yml.
 *
 * Plan B (por defecto): primario Gemini Flash, secundario Groq (gpt-oss-120b);
 * ambos con free tier. Si un proveedor no tiene API key el router lo salta; si
 * ninguno responde, los servicios usan su guion determinista.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
  private String primary = "gemini";
  private String secondary = "groq";
  private final Gemini gemini = new Gemini();
  private final Provider groq = new Provider();
  private final Provider openai = new Provider();
  private final Guard guard = new Guard();

  public static class Provider {
    private String apiKey = "";
    private String model = "";
    private String baseUrl = "";
    private long timeoutMs = 9000;
    private String reasoningEffort = "";

    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
  }

  /** Gemini: modelo principal para el chat y modelo rápido (flash-lite) para clasificar y sugerir. */
  public static class Gemini extends Provider {
    private String modelFast = "";
    private String thinkingLevel = "";
    private int maxOutputTokens = 2048;

    public String getModelFast() { return modelFast == null || modelFast.isBlank() ? getModel() : modelFast; }
    public void setModelFast(String modelFast) { this.modelFast = modelFast; }
    public String getThinkingLevel() { return thinkingLevel; }
    public void setThinkingLevel(String thinkingLevel) { this.thinkingLevel = thinkingLevel; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
  }

  /** Guard de entrada (Llama Prompt Guard 2 en Groq): probabilidad de jailbreak. */
  public static class Guard {
    private boolean enabled = true;
    private String model = "meta-llama/llama-prompt-guard-2-86m";
    private double threshold = 0.8;
    private long timeoutMs = 3000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
  }

  public String getPrimary() { return primary; }
  public void setPrimary(String primary) { this.primary = primary; }
  public String getSecondary() { return secondary; }
  public void setSecondary(String secondary) { this.secondary = secondary; }
  public Gemini getGemini() { return gemini; }
  public Provider getGroq() { return groq; }
  public Provider getOpenai() { return openai; }
  public Guard getGuard() { return guard; }
}
