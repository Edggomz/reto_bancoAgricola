package com.bancoagricola.ruta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API REST de «Ruta» (Bancoagrícola) — módulo de prevención de mora + IA.
 *
 * Cumple el contrato de la app móvil (final/src/api/servicios.ts y tipos.ts).
 * Persiste en Oracle (perfil 'oracle') o en H2 en memoria (por defecto) con el
 * mismo dataset; no hay datos de negocio en el código.
 *
 * IA (Plan B por defecto): Gemini Flash primario + Groq secundario, con
 * fallback automático y guardrails. Configurable por variables de entorno.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RutaApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(RutaApiApplication.class, args);
  }
}
