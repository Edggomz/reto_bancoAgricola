package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.SesionToken;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Ingreso contra CLIENTE (hash SHA-256) y sesiones persistidas en SESION_TOKEN. */
@Service
public class AuthService {
  private static final String CREDENCIALES = "Usuario o clave incorrectos.";
  private static final String SIN_SESION = "Tu sesión terminó. Vuelve a ingresar.";

  private final Repositorios.Clientes clientes;
  private final Repositorios.Tokens tokens;
  private final CopyService copy;

  public AuthService(Repositorios.Clientes clientes, Repositorios.Tokens tokens, CopyService copy) {
    this.clientes = clientes;
    this.tokens = tokens;
    this.copy = copy;
  }

  public record Sesion(String token, String clienteId) {}

  @Transactional
  public Sesion ingresar(String usuario, String clave) {
    String u = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    String c = clave == null ? "" : clave;
    if (u.isEmpty() || c.isEmpty()) throw new IllegalArgumentException("Escribe tu usuario y tu clave.");
    Cliente cliente = clientes.findByUsername(u).filter(Cliente::isActivo)
        .orElseThrow(() -> new UnauthorizedException(CREDENCIALES));
    byte[] esperado = cliente.getPasswordHash().getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(hash(u, c).getBytes(StandardCharsets.UTF_8), esperado)) {
      throw new UnauthorizedException(CREDENCIALES);
    }
    SesionToken token = new SesionToken();
    token.setToken("tok-" + UUID.randomUUID());
    token.setClienteId(cliente.getId());
    token.setExpiresAt(LocalDateTime.now().plusHours(copy.entero("sesion.horas")));
    tokens.save(token);
    return new Sesion(token.getToken(), cliente.getId());
  }

  @Transactional(readOnly = true)
  public String requireCustomerId(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) throw new UnauthorizedException(SIN_SESION);
    return tokens.findById(authorization.substring(7).trim())
        .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
        .map(SesionToken::getClienteId)
        .orElseThrow(() -> new UnauthorizedException(SIN_SESION));
  }

  @Transactional(readOnly = true)
  public App.Perfil perfil(String clienteId) {
    Cliente c = clientes.findById(clienteId).orElseThrow(() -> new UnauthorizedException(SIN_SESION));
    return new App.Perfil(c.getId(), c.getUsername(), c.getDisplayName(), c.getInitials(), c.getAvatarColor(),
        c.getCardTier(), c.getArchetype());
  }

  @Scheduled(cron = "0 30 3 * * *")
  @Transactional
  public void limpiarSesionesVencidas() {
    tokens.deleteByExpiresAtBefore(LocalDateTime.now());
  }

  /** Mismo cálculo que el generador del dataset: SHA-256 de ruta:usuario:clave. */
  public static String hash(String usuario, String clave) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(("ruta:" + usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
