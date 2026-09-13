package com.bancoagricola.ruta.ai;

import com.bancoagricola.ruta.config.AiProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guardrails en código (no solo en el prompt). Un modelo generativo tiende a lo
 * que las reglas prohíben (urgencia, «recuerda pagar», «moroso»); esto lo ataja
 * a la salida. A la entrada, detecta intentos de cambiar las reglas (regex +
 * Llama Prompt Guard) y temas ajenos cuando no hay modelo para clasificar.
 */
@Component
public class Guardrails {
  private static final Pattern MOROSO = Pattern.compile("(?i)\\bmoros[oa]s?\\b");
  private static final Pattern MORA = Pattern.compile("(?i)\\b(caer|caes|caigas|cayó|caído) en mora\\b");
  private static final List<Pattern> EXIGENCIAS = List.of(
      Pattern.compile("(?i)\\b(paga ya|págalo ya|debes pagar|tienes que pagar|recuerda pagar|no olvides pagar|evita cargos|evita recargos)\\b"),
      Pattern.compile("(?i)\\b(urgente|urgentemente|de inmediato|inmediatamente|lo antes posible)\\b"));
  private static final Pattern MARKDOWN = Pattern.compile("[*_#`]+");
  private static final Pattern INYECCION = Pattern.compile("(?i)(ignora|ignorar|olvida|olvidar)\\s+(todas?\\s+)?(tus|las|sus)\\s+(instrucciones|reglas|indicaciones)"
      + "|system prompt|prompt del sistema|actua como|act as|modo desarrollador|developer mode|jailbreak|\\bDAN\\b|revela tus instrucciones|muestrame tu prompt");
  private static final Pattern FUERA_DE_TEMA = Pattern.compile("(?i)\\b(receta|recetas|pelicula|peliculas|serie|series|futbol|partido|clima|chiste|poema|cancion|capital de|"
      + "quien gano|horoscopo|netflix|videojuego|cocinar|hamburguesa|pizza|novia|novio|politica|presidente|elecciones)\\b");

  private final LlmRouter router;
  private final AiProperties props;

  public Guardrails(LlmRouter router, AiProperties props) {
    this.router = router;
    this.props = props;
  }

  /** Limpia términos prohibidos y formato del texto del modelo. */
  public String sanitize(String text) {
    if (text == null) return null;
    String out = MOROSO.matcher(text).replaceAll("cliente");
    out = MORA.matcher(out).replaceAll("atrasarte");
    for (Pattern p : EXIGENCIAS) out = p.matcher(out).replaceAll("cuando te acomode");
    out = MARKDOWN.matcher(out).replaceAll("");
    out = out.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    if (out.length() > 700) out = out.substring(0, 700).trim();
    return out;
  }

  /** true si el texto del cliente intenta cambiar las reglas del asistente. */
  public boolean esInyeccion(String texto, String sesionId) {
    if (texto == null || texto.isBlank()) return false;
    if (INYECCION.matcher(normalizar(texto)).find()) return true;
    return router.probabilidadInyeccion(texto, sesionId).map(p -> p >= props.getGuard().getThreshold()).orElse(false);
  }

  /** Heurística de tema ajeno, solo como respaldo cuando no hay modelo. */
  public boolean esFueraDeTema(String texto) {
    return texto != null && FUERA_DE_TEMA.matcher(normalizar(texto)).find();
  }

  public boolean opcionPermitida(String id, Collection<String> permitidas) {
    return id != null && permitidas.contains(id);
  }

  /** Minúsculas y sin tildes, para comparar etiquetas y buscar palabras clave. */
  public static String normalizar(String texto) {
    if (texto == null) return "";
    String n = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return n.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
  }
}
