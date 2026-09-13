package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.ai.FormAiService;
import com.bancoagricola.ruta.ai.LlmRouter;
import com.bancoagricola.ruta.dto.Api;
import com.bancoagricola.ruta.dto.App;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** IA del formulario (sugerencias pre-resaltadas; la persona decide) y métricas de IA. */
@RestController
public class AiController {
  private final FormAiService form;
  private final LlmRouter router;

  public AiController(FormAiService form, LlmRouter router) {
    this.form = form;
    this.router = router;
  }

  @GetMapping("/ai/sugerencias/frecuencia")
  public App.Sugerencia frecuencia(@CurrentCustomer String clienteId) {
    return form.frecuencia(clienteId);
  }

  @GetMapping("/ai/sugerencias/fecha")
  public App.Sugerencia fecha(@CurrentCustomer String clienteId, @RequestParam String frecuencia) {
    return form.fecha(clienteId, frecuencia);
  }

  @GetMapping("/ai/sugerencias/partes")
  public App.Sugerencia partes(@CurrentCustomer String clienteId, @RequestParam(required = false) String credito) {
    return form.partes(clienteId, credito);
  }

  @PostMapping("/ai/form/suggest")
  public Api.FormSuggestResponse suggest(@RequestBody Api.FormSuggestRequest req) {
    return form.suggest(req);
  }

  @GetMapping("/ai/metrics")
  public Map<String, Object> metrics() {
    return router.metricas();
  }
}
