package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.dto.Voz;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.service.VozService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Canal de voz, servidor a servidor: lo llama n8n, nunca la app. No usa Bearer de
 * cliente (en una llamada no hay sesión): exige X-Voz-Key, y sin VOZ_KEY configurada
 * el canal queda apagado.
 */
@RestController
public class VozController {
  private final VozService voz;
  private final RutaProperties props;

  public VozController(VozService voz, RutaProperties props) {
    this.voz = voz;
    this.props = props;
  }

  private void autorizar(String clave) {
    String esperada = props.vozKey();
    if (esperada == null || esperada.isBlank()) {
      throw new UnauthorizedException("El canal de voz está apagado: falta VOZ_KEY en el servidor.");
    }
    if (clave == null || !MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8))) {
      throw new UnauthorizedException("Falta la cabecera X-Voz-Key o no es válida.");
    }
  }

  @GetMapping("/voz/llamadas/pendientes")
  public Voz.Pendientes pendientes(@RequestHeader(value = "X-Voz-Key", required = false) String clave,
                                   @RequestParam(defaultValue = "10") int limite) {
    autorizar(clave);
    return voz.pendientes(limite);
  }

  @PostMapping("/voz/llamadas")
  public Voz.Programada programar(@RequestHeader(value = "X-Voz-Key", required = false) String clave,
                                  @RequestParam(defaultValue = "false") boolean demo, @RequestBody Voz.Programar req) {
    autorizar(clave);
    return voz.programar(req.clienteId(), demo);
  }

  @PostMapping("/voz/fecha/propuesta")
  public Voz.Propuesta propuesta(@RequestHeader(value = "X-Voz-Key", required = false) String clave,
                                 @RequestBody Voz.PedirPropuesta req) {
    autorizar(clave);
    return voz.propuesta(req);
  }

  @PostMapping("/voz/fecha/confirmar")
  public Voz.Confirmado confirmar(@RequestHeader(value = "X-Voz-Key", required = false) String clave,
                                  @RequestBody Voz.ConfirmarFecha req) {
    autorizar(clave);
    return voz.confirmar(req);
  }

  @PostMapping("/voz/llamadas/resultado")
  public ResponseEntity<Void> resultado(@RequestHeader(value = "X-Voz-Key", required = false) String clave,
                                        @RequestBody Voz.Resultado req) {
    autorizar(clave);
    voz.resultado(req);
    return ResponseEntity.noContent().build();
  }
}
