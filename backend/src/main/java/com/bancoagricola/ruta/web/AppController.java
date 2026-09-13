package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.service.ApartadoService;
import com.bancoagricola.ruta.service.AvisoService;
import com.bancoagricola.ruta.service.CuentaDigitalService;
import com.bancoagricola.ruta.service.DispositivoService;
import com.bancoagricola.ruta.service.FechaCobroService;
import com.bancoagricola.ruta.service.InicioService;
import com.bancoagricola.ruta.service.ProductoService;
import com.bancoagricola.ruta.service.RecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contrato de la app móvil (final/src/api/servicios.ts). Todo requiere Authorization: Bearer. */
@RestController
public class AppController {
  private final InicioService inicio;
  private final FechaCobroService fechaCobro;
  private final ApartadoService apartado;
  private final CuentaDigitalService cuentaDigital;
  private final RecordService record;
  private final AvisoService avisos;
  private final ProductoService productos;
  private final DispositivoService dispositivos;

  public AppController(InicioService inicio, FechaCobroService fechaCobro, ApartadoService apartado,
                       CuentaDigitalService cuentaDigital, RecordService record, AvisoService avisos,
                       ProductoService productos, DispositivoService dispositivos) {
    this.inicio = inicio;
    this.fechaCobro = fechaCobro;
    this.apartado = apartado;
    this.cuentaDigital = cuentaDigital;
    this.record = record;
    this.avisos = avisos;
    this.productos = productos;
    this.dispositivos = dispositivos;
  }

  // --- 02 · 10 · 10b ---
  @GetMapping("/clientes/yo/inicio")
  public App.Inicio inicio(@CurrentCustomer String clienteId) {
    return inicio.inicio(clienteId);
  }

  // --- 03 · 04 · 05 ---
  @PostMapping("/fecha-cobro/frecuencia")
  public ResponseEntity<Void> guardarFrecuencia(@CurrentCustomer String clienteId, @RequestBody App.GuardarFrecuencia req) {
    fechaCobro.guardarFrecuencia(clienteId, req.frecuencia());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/fecha-cobro/opciones")
  public App.OpcionesFecha opcionesFecha(@CurrentCustomer String clienteId, @RequestParam String frecuencia) {
    return fechaCobro.opciones(clienteId, frecuencia);
  }

  @PostMapping("/fecha-cobro")
  public App.FechaConfirmada confirmarFecha(@CurrentCustomer String clienteId, @RequestBody App.ConfirmarFecha req) {
    return fechaCobro.confirmar(clienteId, req.frecuencia(), req.dia(), req.credito(), Boolean.TRUE.equals(req.aceptaInteres())).dto();
  }

  // --- 06 · 07 · 08 · 09 ---
  @GetMapping("/apartado/creditos")
  public List<App.CreditoApartable> creditos(@CurrentCustomer String clienteId) {
    return apartado.creditos(clienteId);
  }

  @GetMapping("/apartado/partes")
  public App.OpcionesPartes partes(@CurrentCustomer String clienteId, @RequestParam(required = false) String credito) {
    return apartado.opciones(clienteId, credito);
  }

  @GetMapping("/apartado/origen")
  public App.OrigenApartado origen(@CurrentCustomer String clienteId, @RequestParam(required = false) String credito,
                                   @RequestParam(required = false) Integer partes) {
    return apartado.origen(clienteId, credito, partes);
  }

  @PostMapping("/apartado")
  public App.Resumen activarApartado(@CurrentCustomer String clienteId, @RequestBody App.ActivarApartado req) {
    boolean automatico = req.automatico() == null || req.automatico();
    return apartado.activar(clienteId, req.credito(), req.partes(), automatico).resumen();
  }

  // --- 08a · 08b ---
  @GetMapping("/cuenta-digital/oferta")
  public App.OfertaCuenta ofertaCuenta() {
    return cuentaDigital.ofertaCuenta();
  }

  @GetMapping("/cuenta-digital/contrato")
  public App.ContratoCuenta contratoCuenta(@CurrentCustomer String clienteId) {
    return cuentaDigital.contrato(clienteId);
  }

  @PostMapping("/cuenta-digital")
  public ResponseEntity<Void> firmarCuenta(@CurrentCustomer String clienteId, @RequestBody App.FirmarCuenta req) {
    cuentaDigital.firmar(clienteId, req.acepta());
    return ResponseEntity.noContent().build();
  }

  // --- 12 ---
  @GetMapping("/clientes/yo/record")
  public App.Record record(@CurrentCustomer String clienteId) {
    return record.record(clienteId);
  }

  // --- 11 ---
  @GetMapping("/clientes/yo/avisos")
  public List<App.Aviso> avisos(@CurrentCustomer String clienteId) {
    return avisos.listar(clienteId);
  }

  @PostMapping("/clientes/yo/avisos/leidos")
  public ResponseEntity<Void> marcarLeidos(@CurrentCustomer String clienteId) {
    avisos.marcarLeidos(clienteId);
    return ResponseEntity.noContent().build();
  }

  // --- Productos disponibles (02) ---
  @PostMapping("/productos/{id}/activar")
  public App.Producto activarProducto(@CurrentCustomer String clienteId, @PathVariable String id) {
    return productos.activar(clienteId, id);
  }

  // --- Push ---
  @PostMapping("/dispositivos")
  public ResponseEntity<Void> registrarDispositivo(@CurrentCustomer String clienteId, @RequestBody App.RegistrarDispositivo req) {
    dispositivos.registrar(clienteId, req.token(), req.plataforma());
    return ResponseEntity.noContent().build();
  }
}
