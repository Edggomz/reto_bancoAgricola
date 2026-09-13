package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.NotificacionEnviada;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import com.bancoagricola.ruta.service.DashboardService;
import com.bancoagricola.ruta.service.PushService;
import com.bancoagricola.ruta.service.RutaMotor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
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

  public AdminController(RutaMotor motor, DashboardService dashboard, PushService push, RutaProperties props,
                         Repositorios.Notificaciones notificaciones, Repositorios.Clientes clientes, Repositorios.Cuentas cuentas) {
    this.motor = motor;
    this.dashboard = dashboard;
    this.push = push;
    this.props = props;
    this.notificaciones = notificaciones;
    this.clientes = clientes;
    this.cuentas = cuentas;
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
