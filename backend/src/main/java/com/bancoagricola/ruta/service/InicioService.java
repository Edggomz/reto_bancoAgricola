package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.dto.App;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inicio (02 · 10 · 10b): el saldo va primero, luego los productos bancarios,
 * «Mi ruta» según lo que exista y los productos disponibles. Todo sale de la
 * base: nombre, cuenta (o su ausencia), créditos, ruta, récord y avisos.
 */
@Service
public class InicioService {
  private final Contexto contexto;
  private final ApartadoService apartado;
  private final ProductoService productos;
  private final RecordService record;
  private final AvisoService avisos;
  private final CopyService copy;

  public InicioService(Contexto contexto, ApartadoService apartado, ProductoService productos, RecordService record,
                       AvisoService avisos, CopyService copy) {
    this.contexto = contexto;
    this.apartado = apartado;
    this.productos = productos;
    this.record = record;
    this.avisos = avisos;
    this.copy = copy;
  }

  @Transactional(readOnly = true)
  public App.Inicio inicio(String clienteId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    App.ClienteInicio cliente = new App.ClienteInicio(datos.cliente().getFirstName(), datos.cliente().getInitials());

    // Cuenta: si no tiene ninguna con nosotros va null y la app lo muestra (y ofrece abrirla).
    App.CuentaInicio cuenta = datos.origen().map(this::aCuenta).orElse(null);

    List<App.CreditoInicio> creditos = new ArrayList<>();
    for (Credito c : datos.creditos()) creditos.add(aCredito(c, datos));

    App.CreditoPrincipal principal = datos.principalConCuota().map(c -> {
      Optional<PlanFechaCobro> plan = datos.plan(c);
      App.Cambio cambio = plan.filter(p -> p.getNewDay() > 0)
          .map(p -> new App.Cambio(p.getNewDay(), p.getEffectiveFrom() == null ? "" : Fechas.mes(p.getEffectiveFrom())))
          .orElse(null);
      return new App.CreditoPrincipal(c.getId(), Fechas.d(c.getInstallmentAmount()), c.diaDeCobro(), cambio);
    }).orElse(null);

    return new App.Inicio(cliente, cuenta, creditos, principal, apartado.miRuta(datos), record.resumen(datos),
        productos.disponibles(datos), (int) avisos.sinLeer(clienteId));
  }

  private App.CuentaInicio aCuenta(Cuenta c) {
    String tipo = copy.texto("cuenta.tipo." + c.getTipo());
    return new App.CuentaInicio(copy.render("inicio.cuenta.producto", Map.of("tipo", tipo, "producto", c.getProductName())),
        Fechas.d(c.getBalanceAvailable()), c.getNumberMasked(), Fechas.d(c.getBalanceApartado()));
  }

  private App.CreditoInicio aCredito(Credito c, Contexto.Datos datos) {
    if (c.esTarjeta()) {
      return new App.CreditoInicio(c.getId(), "tarjeta", c.getName(), Fechas.d(c.getAvailable()),
          copy.render("inicio.tarjeta.detalle", Map.of("limite", Fechas.monto(c.getCreditLimit()))), c.isApartable());
    }
    Optional<PlanFechaCobro> plan = datos.plan(c).filter(p -> p.getNewDay() > 0);
    String detalle = plan.map(p -> copy.render("inicio.credito.detalle_cambio",
            Map.of("dia", p.getNewDay(), "mes", p.getEffectiveFrom() == null ? "" : Fechas.mes(p.getEffectiveFrom()))))
        .orElseGet(() -> copy.render("inicio.credito.detalle", Map.of("dia", c.diaDeCobro())));
    return new App.CreditoInicio(c.getId(), c.getKind(), c.getName(), Fechas.d(c.getInstallmentAmount()), detalle, c.isApartable());
  }
}
