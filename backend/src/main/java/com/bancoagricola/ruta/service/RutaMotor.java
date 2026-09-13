package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Apartado;
import com.bancoagricola.ruta.domain.ApartadoCuota;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.NotificacionEnviada;
import com.bancoagricola.ruta.domain.ShockContext;
import com.bancoagricola.ruta.domain.Transaccion;
import com.bancoagricola.ruta.repository.Repositorios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Motor diario de la ruta (@Scheduled). Recorre la base y:
 *  1. Avisa por push a quien está a N días de su corte con saldo pendiente
 *     (N = ruta.push.dias-aviso, por defecto 5). Se decide por ciclo y saldo,
 *     nunca por el riesgo del cliente.
 *  2. Aparta (congela) cada parte el día que toca; si no alcanza, nace el
 *     contexto de choque y el aviso «Este mes vino distinto, y está bien».
 *  3. Paga la cuota completa el día del cobro con lo apartado y suma +1 al récord.
 *
 * Todos los avisos CONFIRMAN lo ocurrido; ninguno recuerda pagar. La fecha se
 * puede simular desde POST /api/admin/ruta/procesar?fecha=AAAA-MM-DD para la demo.
 */
@Service
public class RutaMotor {
  private static final Logger log = LoggerFactory.getLogger(RutaMotor.class);
  private static final List<String> ENVIADOS = List.of(NotificacionEnviada.ENVIADO, NotificacionEnviada.SIMULADO);

  private final Repositorios.Clientes clientes;
  private final Repositorios.Creditos creditos;
  private final Repositorios.Cuentas cuentas;
  private final Repositorios.Transacciones transacciones;
  private final Repositorios.Apartados apartados;
  private final Repositorios.ApartadoCuotas cuotas;
  private final Repositorios.Notificaciones notificaciones;
  private final Repositorios.Choques choques;
  private final CopyService copy;
  private final AvisoService avisos;
  private final RecordService record;
  private final RutaProperties props;

  public RutaMotor(Repositorios.Clientes clientes, Repositorios.Creditos creditos, Repositorios.Cuentas cuentas,
                   Repositorios.Transacciones transacciones, Repositorios.Apartados apartados,
                   Repositorios.ApartadoCuotas cuotas, Repositorios.Notificaciones notificaciones,
                   Repositorios.Choques choques, CopyService copy, AvisoService avisos, RecordService record,
                   RutaProperties props) {
    this.clientes = clientes;
    this.creditos = creditos;
    this.cuentas = cuentas;
    this.transacciones = transacciones;
    this.apartados = apartados;
    this.cuotas = cuotas;
    this.notificaciones = notificaciones;
    this.choques = choques;
    this.copy = copy;
    this.avisos = avisos;
    this.record = record;
    this.props = props;
  }

  @Scheduled(cron = "${ruta.push.cron}", zone = "${ruta.push.zona}")
  public void diario() {
    LocalDate hoy = LocalDate.now(ZoneId.of(props.push().zona()));
    log.info("Motor de la ruta: corrida diaria {}", hoy);
    procesar(hoy);
  }

  @Transactional
  public Map<String, Object> procesar(LocalDate hoy) {
    Map<String, Object> resumen = new LinkedHashMap<>();
    resumen.put("fecha", hoy.toString());
    resumen.put("diasAviso", props.push().diasAviso());
    resumen.put("corteCercano", avisarCorteCercano(hoy));
    Map<String, Integer> partes = apartarPartes(hoy);
    resumen.put("partesApartadas", partes.get("apartadas"));
    resumen.put("partesSinSaldo", partes.get("sinSaldo"));
    resumen.put("cuotasPagadas", pagarCuotas(hoy));
    log.info("Motor de la ruta {}: {}", hoy, resumen);
    return resumen;
  }

  // --- 1. Corte cercano -------------------------------------------------------
  private int avisarCorteCercano(LocalDate hoy) {
    int enviados = 0;
    int diasAviso = props.push().diasAviso();
    String tipo = copy.texto("push.tipo");
    for (Cliente c : clientes.findByActivoTrue()) {
      for (Credito cr : creditos.findByClienteIdOrderByKindAscIdAsc(c.getId())) {
        LocalDate corte = CalendarioPagos.corteSiguiente(cr.getDiaCorte(), hoy);
        long dias = corte.toEpochDay() - hoy.toEpochDay();
        if (dias != diasAviso) continue;
        if (saldoCiclo(cr, hoy).signum() <= 0) continue;
        if (notificaciones.existsByCreditoIdAndTipoAndCorteFechaAndEstadoIn(cr.getId(), tipo, corte, ENVIADOS)) continue;
        String cuerpo = dias == 1 ? copy.render("push.cuerpo_un_dia", Map.of("nombre", c.getFirstName()))
            : copy.render("push.cuerpo", Map.of("nombre", c.getFirstName(), "dias", dias));
        avisos.emitir(c.getId(), AvisoService.CORTE, copy.texto("push.titulo"), cuerpo, null, cr.getId(), tipo, corte, (int) dias);
        enviados++;
      }
    }
    return enviados;
  }

  /** Saldo del ciclo vigente (misma regla que VW_SALDO_CICLO): cargos menos abonos entre cortes. */
  public BigDecimal saldoCiclo(Credito cr, LocalDate hoy) {
    LocalDate anterior = CalendarioPagos.corteAnterior(cr.getDiaCorte(), hoy);
    LocalDate siguiente = CalendarioPagos.corteSiguiente(cr.getDiaCorte(), hoy);
    BigDecimal saldo = BigDecimal.ZERO;
    for (Transaccion t : transacciones.findByCreditoIdAndFechaGreaterThanAndFechaLessThanEqual(cr.getId(), anterior, siguiente)) {
      saldo = "D".equals(t.getTipo()) ? saldo.add(t.getMonto()) : saldo.subtract(t.getMonto());
    }
    return saldo;
  }

  // --- 2. Apartar partes ------------------------------------------------------
  private Map<String, Integer> apartarPartes(LocalDate hoy) {
    int apartadas = 0;
    int sinSaldo = 0;
    for (ApartadoCuota q : cuotas.findByFechaLessThanEqualAndEstadoOrderByFechaAsc(hoy, ApartadoCuota.PENDIENTE)) {
      Apartado a = apartados.findById(q.getApartadoId()).orElse(null);
      if (a == null || !Apartado.ACTIVO.equals(a.getEstado())) continue;
      Credito cr = creditos.findById(a.getCreditoId()).orElse(null);
      Cuenta cta = cuentas.findById(a.getSourceAccountId()).orElse(null);
      if (cr == null || cta == null) continue;
      Cliente c = clientes.findById(cr.getClienteId()).orElse(null);
      if (c == null) continue;
      List<ApartadoCuota> todas = cuotas.findByApartadoIdOrderByIdxAsc(a.getId());
      if (cta.getBalanceAvailable().compareTo(q.getAmount()) >= 0) {
        cta.setBalanceAvailable(cta.getBalanceAvailable().subtract(q.getAmount()));
        cta.setBalanceApartado(cta.getBalanceApartado().add(q.getAmount()));
        cuentas.save(cta);
        q.setEstado(ApartadoCuota.APARTADA);
        cuotas.save(q);
        long hechas = todas.stream().filter(x -> ApartadoCuota.APARTADA.equals(x.getEstado()) || x.getIdx().equals(q.getIdx())).count();
        boolean ultima = hechas >= todas.size();
        String titulo;
        String cuerpo;
        String kind;
        if (ultima) {
          kind = AvisoService.COMPLETE;
          titulo = copy.texto("aviso.completa.titulo");
          cuerpo = copy.render("aviso.completa.cuerpo", Map.of("monto", Fechas.monto(q.getAmount()), "dia", a.getFechaPago().getDayOfMonth()));
        } else {
          kind = AvisoService.PROGRESS;
          titulo = hechas * 2 == todas.size() ? copy.texto("aviso.parte.mitad")
              : copy.render("aviso.parte.titulo", Map.of("n", hechas, "total", todas.size()));
          String progreso = hechas * 2 == todas.size() ? "a la mitad" : "en " + hechas + " de " + todas.size() + " partes";
          cuerpo = copy.render("aviso.parte.cuerpo", Map.of("monto", Fechas.monto(q.getAmount()),
              "fecha", Fechas.etiqueta(a.getFechaPago()), "progreso", progreso));
        }
        avisos.emitir(c.getId(), kind, titulo, cuerpo, null, cr.getId(), "parte-apartada", q.getFecha(), 0);
        apartadas++;
      } else {
        q.setEstado(ApartadoCuota.NO_ALCANZO);
        cuotas.save(q);
        cr.setEstadoPago(Credito.PARTE_PENDIENTE);
        creditos.save(cr);
        ShockContext s = choques.findById(c.getId()).orElseGet(ShockContext::new);
        s.setClienteId(c.getId());
        s.setCreditoId(cr.getId());
        s.setEventLabel(copy.render("aviso.choque.evento", Map.of("fecha", Fechas.etiqueta(q.getFecha()))));
        s.setReassurance(copy.texto("chat.tranquilidad"));
        s.setAmount(q.getAmount());
        s.setCurrency(cta.getCurrency());
        s.setNextDateLabel(Fechas.etiqueta(a.getFechaPago().plusMonths(1)));
        choques.save(s);
        String pago = "quincena";
        avisos.emitir(c.getId(), AvisoService.SHOCK, copy.texto("aviso.choque.titulo"),
            copy.render("aviso.choque.cuerpo", Map.of("monto", Fechas.monto(q.getAmount()), "pago", pago)),
            "advisory-shock", cr.getId(), "parte-no-alcanzo", q.getFecha(), 0);
        sinSaldo++;
      }
    }
    return Map.of("apartadas", apartadas, "sinSaldo", sinSaldo);
  }

  // --- 3. Pagar cuotas --------------------------------------------------------
  private int pagarCuotas(LocalDate hoy) {
    int pagadas = 0;
    for (Apartado a : apartados.findByEstadoAndFechaPagoLessThanEqual(Apartado.ACTIVO, hoy)) {
      List<ApartadoCuota> todas = cuotas.findByApartadoIdOrderByIdxAsc(a.getId());
      Cuenta cta = cuentas.findById(a.getSourceAccountId()).orElse(null);
      Credito cr = creditos.findById(a.getCreditoId()).orElse(null);
      if (cta == null || cr == null) continue;
      BigDecimal apartado = todas.stream().filter(x -> ApartadoCuota.APARTADA.equals(x.getEstado()))
          .map(ApartadoCuota::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal faltante = a.getMontoTotal().subtract(apartado);
      if (faltante.signum() > 0 && cta.getBalanceAvailable().compareTo(faltante) < 0) continue; // sigue activa: el choque ya avisó
      if (faltante.signum() > 0) cta.setBalanceAvailable(cta.getBalanceAvailable().subtract(faltante));
      cta.setBalanceApartado(cta.getBalanceApartado().subtract(apartado).max(BigDecimal.ZERO));
      cuentas.save(cta);
      Transaccion t = new Transaccion();
      t.setId("tx-" + UUID.randomUUID());
      t.setCreditoId(cr.getId());
      t.setNumeroProducto(cr.getNumberMasked());
      t.setFecha(hoy);
      t.setTipo("H");
      t.setMonto(a.getMontoTotal());
      t.setDescripcion("Pago de cuota con lo apartado");
      transacciones.save(t);
      a.setEstado("cumplido");
      apartados.save(a);
      cr.setEstadoPago(Credito.AL_DIA);
      creditos.save(cr);
      int meses = record.registrarPagoATiempo(cr.getClienteId()).getStreakMonths();
      record.sumar(cr.getClienteId(), copy.render("record.sumando.pagado", Map.of("mes", Fechas.mes(hoy))));
      avisos.emitir(cr.getClienteId(), AvisoService.PAID, copy.texto("aviso.pagado.titulo"),
          copy.render("aviso.pagado.cuerpo", Map.of("monto", Fechas.monto(a.getMontoTotal()), "meses", meses)),
          "record", cr.getId(), "cuota-pagada", hoy, 0);
      pagadas++;
    }
    return pagadas;
  }
}
