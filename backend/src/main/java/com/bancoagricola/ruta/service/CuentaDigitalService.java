package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.OfertaApertura;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abre tu cuenta (08a · 08b): la puerta de fidelización de quien solo tiene
 * crédito. Solo se puede apartar desde una cuenta de este banco.
 */
@Service
public class CuentaDigitalService {
  private final Contexto contexto;
  private final Repositorios.OfertasApertura ofertas;
  private final Repositorios.CondicionesApertura condiciones;
  private final Repositorios.DocumentosApertura documentos;
  private final Repositorios.Cuentas cuentas;
  private final CopyService copy;
  private final AvisoService avisos;
  private final AuditoriaService auditoria;

  public CuentaDigitalService(Contexto contexto, Repositorios.OfertasApertura ofertas,
                              Repositorios.CondicionesApertura condiciones, Repositorios.DocumentosApertura documentos,
                              Repositorios.Cuentas cuentas, CopyService copy, AvisoService avisos, AuditoriaService auditoria) {
    this.contexto = contexto;
    this.ofertas = ofertas;
    this.condiciones = condiciones;
    this.documentos = documentos;
    this.cuentas = cuentas;
    this.copy = copy;
    this.avisos = avisos;
    this.auditoria = auditoria;
  }

  private OfertaApertura oferta() {
    return ofertas.findFirstByActivoTrueOrderByIdAsc()
        .orElseThrow(() -> new IllegalStateException("No hay oferta de apertura activa en OFERTA_APERTURA."));
  }

  @Transactional(readOnly = true)
  public App.OfertaCuenta ofertaCuenta() {
    OfertaApertura o = oferta();
    return new App.OfertaCuenta(o.getProductName(),
        condiciones.findByOfertaIdOrderByIdxAsc(o.getId()).stream().map(c -> c.getTexto()).toList());
  }

  @Transactional(readOnly = true)
  public App.ContratoCuenta contrato(String clienteId) {
    Cliente c = contexto.cliente(clienteId);
    OfertaApertura o = oferta();
    List<App.Fila> filas = List.of(
        new App.Fila(copy.texto("cuenta.contrato.titular"), c.getDisplayName()),
        new App.Fila(copy.texto("cuenta.contrato.cuenta"), o.getAccountProductName()),
        new App.Fila(copy.texto("cuenta.contrato.apertura"), Fechas.monto(o.getOpeningCost())),
        new App.Fila(copy.texto("cuenta.contrato.mensual"), Fechas.monto(o.getMonthlyCost())),
        new App.Fila(copy.texto("cuenta.contrato.para"), copy.texto("cuenta.contrato.para_valor")));
    List<App.Documento> docs = documentos.findByOfertaIdOrderByOrdenAsc(o.getId()).stream()
        .map(d -> new App.Documento(d.getTitulo(), d.getUrl())).toList();
    return new App.ContratoCuenta(filas, docs);
  }

  @Transactional
  public Cuenta firmar(String clienteId, Boolean acepta) {
    if (acepta == null || !acepta) throw new IllegalArgumentException(copy.texto("apertura.debe_aceptar"));
    Contexto.Datos datos = contexto.cargar(clienteId);
    OfertaApertura o = oferta();
    String numero;
    do {
      numero = "3007" + ThreadLocalRandom.current().nextInt(10, 99) + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000));
    } while (cuentas.existsByNumberFull(numero));
    Cuenta c = new Cuenta();
    c.setId("acc-" + numero.substring(numero.length() - 4) + "-" + ThreadLocalRandom.current().nextInt(100, 999));
    c.setClienteId(clienteId);
    c.setTipo(o.getAccountTipo());
    c.setProductName(o.getAccountProductName());
    c.setNumberMasked("····" + numero.substring(numero.length() - 4));
    c.setNumberFull(numero);
    c.setBalanceAvailable(BigDecimal.ZERO);
    c.setBalanceApartado(BigDecimal.ZERO);
    c.setCurrency(copy.texto("moneda.default"));
    c.setPrimarySource(datos.cuentas().stream().noneMatch(Cuenta::isPrimarySource));
    cuentas.save(c);
    avisos.emitir(clienteId, AvisoService.CONFIRM, copy.texto("aviso.cuenta.titulo"),
        copy.render("aviso.cuenta.cuerpo", Map.of("producto", c.getProductName(), "numero", c.getNumberMasked())),
        null, null, "cuenta-abierta");
    auditoria.info(AuditoriaService.APP, "cuenta.abierta", clienteId, c.getId(), "Abrió su " + c.getProductName() + " " + c.getNumberMasked(), null);
    return c;
  }
}
