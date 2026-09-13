package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.ai.AdvisorAiService;
import com.bancoagricola.ruta.dto.App;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 13 · 14 · 14b · 15: el chat ES el asesor. Resuelve primero; escala solo si se sale de las manos. */
@RestController
public class AsesorController {
  private final AdvisorAiService asesor;

  public AsesorController(AdvisorAiService asesor) {
    this.asesor = asesor;
  }

  @PostMapping("/asesor/sesiones")
  public App.SesionAsesor iniciar(@CurrentCustomer String clienteId, @RequestBody(required = false) App.IniciarSesion req) {
    return asesor.iniciar(clienteId, req == null ? null : req.aviso());
  }

  @PostMapping("/asesor/sesiones/{sesion}/mensajes")
  public App.RespuestaAsesor enviar(@CurrentCustomer String clienteId, @PathVariable String sesion, @RequestBody App.EnviarMensaje req) {
    return asesor.enviar(clienteId, sesion, req == null ? "" : req.texto());
  }

  @PostMapping("/asesor/sesiones/{sesion}/fin")
  public ResponseEntity<Void> terminar(@CurrentCustomer String clienteId, @PathVariable String sesion) {
    asesor.terminar(clienteId, sesion);
    return ResponseEntity.noContent().build();
  }
}
