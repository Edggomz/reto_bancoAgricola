package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.dto.Api;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  /** 01 · Ingreso. La app manda {usuario, clave} y guarda el token como Bearer. */
  @PostMapping("/auth/ingreso")
  public App.Token ingresar(@RequestBody App.Ingreso req) {
    return new App.Token(auth.ingresar(req.usuario(), req.clave()).token());
  }

  /** Alias del contrato anterior (movil_BancoAgricola): {username, password} -> {token, customerId}. */
  @PostMapping("/auth/login")
  public Api.AuthSession login(@RequestBody Api.LoginRequest req) {
    AuthService.Sesion s = auth.ingresar(req.username(), req.password());
    return new Api.AuthSession(s.token(), s.clienteId());
  }

  @GetMapping("/clientes/yo")
  public App.Perfil perfil(@CurrentCustomer String clienteId) {
    return auth.perfil(clienteId);
  }
}
