package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Dispositivo;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** POST /dispositivos: el teléfono registra su token de push al iniciar sesión. */
@Service
public class DispositivoService {
  private static final Set<String> PLATAFORMAS = Set.of("android", "ios", "web");

  private final Repositorios.Dispositivos dispositivos;

  public DispositivoService(Repositorios.Dispositivos dispositivos) {
    this.dispositivos = dispositivos;
  }

  @Transactional
  public Dispositivo registrar(String clienteId, String token, String plataforma) {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Falta el token del dispositivo.");
    String p = plataforma == null ? "android" : plataforma.trim().toLowerCase(Locale.ROOT);
    if (!PLATAFORMAS.contains(p)) p = "android";
    Dispositivo d = dispositivos.findByPushToken(token.trim()).orElseGet(Dispositivo::new);
    if (d.getId() == null) d.setId("disp-" + UUID.randomUUID());
    d.setClienteId(clienteId);
    d.setPushToken(token.trim());
    d.setPlatform(p);
    d.setActivo(true);
    return dispositivos.save(d);
  }
}
