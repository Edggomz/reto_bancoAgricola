package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Apartado;
import com.bancoagricola.ruta.domain.ApartadoCuota;
import com.bancoagricola.ruta.domain.Autopago;
import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Apartar la cuota (06 -> 07 -> 08 -> 09). APARTAR NO ES ABONAR: el dinero se
 * congela en la cuenta del cliente y se paga completo el día del cobro.
 *
 * Regla de partes: tantas como veces recibe dinero. Quien cobra una vez al mes
 * aparta en 1 parte; quincena y fin de mes en 2 (o 3-4); cada semana hasta 4.
 * El número de partes que manda la app se valida aquí contra esa regla.
 */
@Service
public class ApartadoService {
  private final Contexto contexto;
  private final CalendarioPagos calendario;
  private final Repositorios.Apartados apartados;
  private final Repositorios.ApartadoCuotas cuotas;
  private final Repositorios.Autopagos autopagos;
  private final CopyService copy;
  private final AvisoService avisos;
  private final RecordService record;

  public ApartadoService(Contexto contexto, CalendarioPagos calendario, Repositorios.Apartados apartados,
                         Repositorios.ApartadoCuotas cuotas, Repositorios.Autopagos autopagos, CopyService copy,
                         AvisoService avisos, RecordService record) {
    this.contexto = contexto;
    this.calendario = calendario;
    this.apartados = apartados;
    this.cuotas = cuotas;
    this.autopagos = autopagos;
    this.copy = copy;
    this.avisos = avisos;
    this.record = record;
  }

  public record Parte(int idx, LocalDate fecha, BigDecimal monto) {}
  public record Plan(Credito credito, CatalogoFrecuencia frecuencia, int partes, LocalDate fechaPago, int diaCobro,
                     List<Parte> cuotas) {
    public BigDecimal total() {
      return credito.montoApartable();
    }

    public List<LocalDate> fechas() {
      return cuotas.stream().map(Parte::fecha).toList();
    }
  }

  // --- 06 ---------------------------------------------------------------------
  @Transactional(readOnly = true)
  public List<App.CreditoApartable> creditos(String clienteId) {
    return contexto.cargar(clienteId).apartables().stream().map(this::aApartable).toList();
  }

  private App.CreditoApartable aApartable(Credito c) {
    boolean tarjeta = c.esTarjeta();
    return new App.CreditoApartable(c.getId(), c.getName(),
        tarjeta ? "Visa " + c.getNumberMasked() : "N.º " + c.ultimos4(),
        Fechas.d(c.montoApartable()),
        copy.texto(tarjeta ? "apartado.credito.nota.card" : "apartado.credito.nota.personal"),
        tarjeta ? "tarjeta" : "bolsaDinero");
  }

  // --- 07 ---------------------------------------------------------------------
  public Credito creditoApartable(Contexto.Datos datos, String creditoId) {
    Optional<Credito> c = creditoId == null || creditoId.isBlank() ? datos.principalApartable() : datos.credito(creditoId);
    return c.filter(Credito::isApartable)
        .orElseThrow(() -> new IllegalArgumentException("No encontramos ese crédito entre los que puedes apartar."));
  }

  public List<Integer> partesPermitidas(Contexto.Datos datos) {
    return calendario.delClienteODefault(datos).partes();
  }

  @Transactional(readOnly = true)
  public App.OpcionesPartes opciones(String clienteId, String creditoId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Credito c = creditoApartable(datos, creditoId);
    CatalogoFrecuencia f = calendario.delClienteODefault(datos);
    List<App.OpcionPartes> opciones = new ArrayList<>();
    for (Integer n : f.partes()) {
      Plan plan = plan(datos, c, n);
      opciones.add(new App.OpcionPartes(n, calendario(plan)));
    }
    String sugerencia = f.getNotaPartes() == null ? copy.texto("apartado.nota") : f.getNotaPartes();
    return new App.OpcionesPartes(new App.CreditoPartes(c.getName(), Fechas.d(c.montoApartable()), diaCobro(datos, c)),
        sugerencia, opciones);
  }

  private List<App.FilaCalendario> calendario(Plan plan) {
    List<String> ordinales = copy.lista("apartado.ordinales");
    List<App.FilaCalendario> filas = new ArrayList<>();
    for (Parte p : plan.cuotas()) {
      String detalle = plan.partes() == 1 ? copy.texto("apartado.calendario.aparta_una")
          : copy.render("apartado.calendario.aparta", Map.of("n", ordinales.get(Math.min(p.idx() - 1, ordinales.size() - 1))));
      filas.add(new App.FilaCalendario(Fechas.etiqueta(p.fecha()), detalle, Fechas.d(p.monto()), "aparta"));
    }
    filas.add(new App.FilaCalendario(Fechas.etiqueta(plan.fechaPago()), copy.texto("apartado.calendario.paga"),
        Fechas.d(plan.total()), "paga"));
    return filas;
  }

  public int diaCobro(Contexto.Datos datos, Credito c) {
    return datos.plan(c).map(PlanFechaCobro::getNewDay).filter(d -> d > 0).orElse(c.diaDeCobro());
  }

  /** Arma el calendario de N partes que terminan en el último pago antes del cobro. */
  public Plan plan(Contexto.Datos datos, Credito c, int partes) {
    CatalogoFrecuencia f = calendario.delClienteODefault(datos);
    if (!f.partes().contains(partes)) {
      String permitidas = Fechas.enumerar(f.partes().stream().map(n -> n == 1 ? "1 parte" : n + " partes").toList());
      throw new IllegalArgumentException(copy.render("apartado.partes_invalidas", Map.of("permitidas", permitidas)));
    }
    LocalDate hoy = LocalDate.now();
    int dia = diaCobro(datos, c);
    LocalDate minimo = hoy.plusDays(copy.entero("apartado.min_dias_para_cobro"));
    LocalDate efectivo = datos.plan(c).map(PlanFechaCobro::getEffectiveFrom).orElse(null);
    if (efectivo != null && efectivo.isAfter(minimo)) minimo = efectivo;
    LocalDate fechaPago = Fechas.desdeDia(minimo, dia);

    List<Integer> variables = f.isVariable() ? calendario.diasVariables(datos.creditoIds(), hoy) : List.of();
    LocalDate ultimoPago = calendario.ultimoPagoAntes(f, variables, fechaPago);
    // 2 partes cada 15 días, 3 cada 10, 4 cada 7 (una por semana), terminando en el último pago.
    int espacio = partes == 1 ? 0 : partes == 4 ? 7 : (int) Math.round(30.0 / partes);
    List<LocalDate> fechas = new ArrayList<>();
    for (int i = 0; i < partes; i++) fechas.add(ultimoPago.minusDays((long) espacio * (partes - 1 - i)));
    if (fechas.get(0).isBefore(hoy.plusDays(1))) {
      LocalDate inicio = hoy.plusDays(1);
      long tramo = partes == 1 ? 0 : Math.max(1, (ultimoPago.toEpochDay() - inicio.toEpochDay()) / (partes - 1));
      fechas.clear();
      for (int i = 0; i < partes; i++) fechas.add(i == partes - 1 ? ultimoPago : inicio.plusDays(tramo * i));
    }

    long centavos = c.montoApartable().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    long base = centavos / partes;
    long resto = centavos - base * partes;
    List<Parte> lista = new ArrayList<>();
    for (int i = 0; i < partes; i++) {
      long monto = base + (i < resto ? 1 : 0);
      lista.add(new Parte(i + 1, fechas.get(i), BigDecimal.valueOf(monto).movePointLeft(2)));
    }
    return new Plan(c, f, partes, fechaPago, dia, lista);
  }

  // --- 08 ---------------------------------------------------------------------
  @Transactional(readOnly = true)
  public App.OrigenApartado origen(String clienteId, String creditoId, Integer partes) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Optional<Cuenta> cuenta = datos.origen();
    App.CuentaOrigen origen = cuenta.map(this::aOrigen).orElse(null);
    List<String> pasos;
    if (partes != null && partes > 0) {
      Plan plan = plan(datos, creditoApartable(datos, creditoId), partes);
      pasos = pasos(plan);
    } else {
      pasos = List.of("De cada pago que recibes apartamos una parte", "El día del cobro pagamos tu cuota completa",
          copy.texto("apartado.paso.aviso"));
    }
    return new App.OrigenApartado(origen, pasos);
  }

  public App.CuentaOrigen aOrigen(Cuenta cuenta) {
    String tipo = copy.texto("cuenta.tipo." + cuenta.getTipo());
    return new App.CuentaOrigen(copy.render("apartado.origen.titulo", Map.of("tipo", tipo)),
        copy.render("apartado.origen.nombre", Map.of("numero", cuenta.getNumberMasked())),
        copy.texto("apartado.origen.detalle"));
  }

  public List<String> pasos(Plan plan) {
    String dias = Fechas.enumerarDias(plan.fechas());
    String paso1 = plan.partes() == 1
        ? copy.render("apartado.paso.una", Map.of("dias", dias, "monto", Fechas.monto(plan.total())))
        : copy.render("apartado.paso.varias", Map.of("dias", dias, "monto", montos(plan)));
    return List.of(paso1, copy.render("apartado.paso.paga", Map.of("dia", plan.fechaPago().getDayOfMonth())),
        copy.texto("apartado.paso.aviso"));
  }

  private static String montos(Plan plan) {
    List<Parte> c = plan.cuotas();
    boolean iguales = c.stream().allMatch(p -> p.monto().compareTo(c.get(0).monto()) == 0);
    return iguales ? Fechas.monto(c.get(0).monto()) : Fechas.enumerar(c.stream().map(p -> Fechas.monto(p.monto())).toList());
  }

  // --- 09 ---------------------------------------------------------------------
  public record Activacion(App.Resumen resumen, Apartado apartado, Plan plan, Cuenta cuenta) {}

  @Transactional
  public Activacion activar(String clienteId, String creditoId, Integer partes, boolean automatico) {
    if (partes == null) throw new IllegalArgumentException("Elige en cuántas partes apartamos.");
    Contexto.Datos datos = contexto.cargar(clienteId);
    Credito c = creditoApartable(datos, creditoId);
    Cuenta cuenta = datos.origen().orElseThrow(() -> new IllegalArgumentException(copy.texto("apartado.sin_cuenta")));
    Plan plan = plan(datos, c, partes);

    // Un crédito tiene a lo sumo un apartado activo: el nuevo reemplaza al anterior.
    // (flush explícito: Hibernate ejecuta los INSERT antes que los UPDATE y chocaría con el índice único)
    for (Apartado previo : apartados.findByCreditoIdAndEstado(c.getId(), Apartado.ACTIVO)) {
      previo.setEstado(Apartado.CANCELADO);
      apartados.saveAndFlush(previo);
    }
    Apartado a = new Apartado();
    a.setId("apartado-" + UUID.randomUUID());
    a.setCreditoId(c.getId());
    a.setParts(partes);
    a.setSourceAccountId(cuenta.getId());
    a.setPaysOnLabel(copy.render("apartado.se_paga", Map.of("fecha", Fechas.etiqueta(plan.fechaPago()))));
    a.setAutomatic(automatico);
    a.setFirstFullInstallmentLabel(copy.render("apartado.primera_cuota", Map.of("fecha", Fechas.etiqueta(plan.fechaPago()))));
    a.setMontoTotal(plan.total());
    a.setFechaPago(plan.fechaPago());
    a.setEstado(Apartado.ACTIVO);
    apartados.saveAndFlush(a);
    for (Parte p : plan.cuotas()) {
      ApartadoCuota q = new ApartadoCuota();
      q.setApartadoId(a.getId());
      q.setIdx(p.idx());
      q.setLabel(Fechas.etiqueta(p.fecha()));
      q.setFecha(p.fecha());
      q.setAmount(p.monto());
      q.setEstado(ApartadoCuota.PENDIENTE);
      cuotas.save(q);
    }
    if (automatico) {
      for (Autopago previo : autopagos.findByCreditoIdAndActiveTrue(c.getId())) {
        previo.setActive(false);
        autopagos.saveAndFlush(previo);
      }
      Autopago ap = new Autopago();
      ap.setId("autopay-" + UUID.randomUUID());
      ap.setCreditoId(c.getId());
      ap.setAccountId(cuenta.getId());
      ap.setActive(true);
      autopagos.save(ap);
    }
    record.sumar(clienteId, copy.texto("record.sumando.apartas"));
    avisos.emitir(clienteId, AvisoService.CONFIRM, copy.texto("aviso.ruta.titulo"),
        copy.render("aviso.ruta.cuerpo", Map.of("monto", montos(plan), "dias", Fechas.enumerarDias(plan.fechas()),
            "dia", plan.fechaPago().getDayOfMonth())),
        null, c.getId(), "ruta-lista");
    return new Activacion(resumen(plan, cuenta, automatico), a, plan, cuenta);
  }

  public App.Resumen resumen(Plan plan, Cuenta cuenta, boolean automatico) {
    String tipo = copy.texto("cuenta.tipo." + cuenta.getTipo());
    String tipoCapital = Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1);
    List<App.Fila> filas = new ArrayList<>();
    filas.add(new App.Fila(copy.texto("resumen.fila.cobro"), copy.render("resumen.fila.cobro_valor", Map.of("dia", plan.diaCobro()))));
    filas.add(new App.Fila(copy.texto("resumen.fila.apartamos"),
        copy.render("resumen.fila.apartamos_valor", Map.of("monto", montos(plan), "dias", Fechas.enumerarDias(plan.fechas())))));
    filas.add(new App.Fila(copy.texto("resumen.fila.desde"),
        copy.render("resumen.fila.desde_valor", Map.of("tipo", tipoCapital, "numero", cuenta.getNumberMasked()))));
    filas.add(new App.Fila(copy.texto("resumen.fila.primera"), Fechas.etiqueta(plan.fechaPago())));
    filas.add(new App.Fila(copy.texto("resumen.fila.automatico"),
        copy.texto(automatico ? "resumen.fila.automatico_si" : "resumen.fila.automatico_no")));
    return new App.Resumen(filas);
  }

  /** Activa el débito automático sobre el apartado vigente (desde el chat). */
  @Transactional
  public Optional<Apartado> dejarEnAutomatico(String clienteId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Optional<Apartado> activo = datos.apartadoActivo();
    if (activo.isEmpty()) return Optional.empty();
    Apartado a = activo.get();
    if (!a.isAutomatic()) {
      a.setAutomatic(true);
      apartados.save(a);
      if (autopagos.findByCreditoIdAndActiveTrue(a.getCreditoId()).isEmpty()) {
        Autopago ap = new Autopago();
        ap.setId("autopay-" + UUID.randomUUID());
        ap.setCreditoId(a.getCreditoId());
        ap.setAccountId(a.getSourceAccountId());
        ap.setActive(true);
        autopagos.save(ap);
      }
    }
    return Optional.of(a);
  }

  /** Hitos de «Mi ruta» en el inicio (10). */
  public App.MiRuta miRuta(Contexto.Datos datos) {
    Optional<Apartado> activo = datos.apartadoActivo();
    if (activo.isPresent()) {
      Apartado a = activo.get();
      List<App.Hito> hitos = new ArrayList<>();
      for (ApartadoCuota q : cuotas.findByApartadoIdOrderByIdxAsc(a.getId())) {
        hitos.add(new App.Hito(Fechas.corta(q.getFecha()),
            copy.render("inicio.ruta.aparta", Map.of("monto", Fechas.monto(q.getAmount()))), "aparta"));
      }
      hitos.add(new App.Hito(Fechas.corta(a.getFechaPago()),
          copy.render("inicio.ruta.paga", Map.of("monto", Fechas.monto(a.getMontoTotal()))), "paga"));
      return new App.MiRuta("activa", hitos, null);
    }
    Optional<PlanFechaCobro> plan = datos.plan();
    if (plan.isPresent() && plan.get().getNewDay() > 0) {
      return new App.MiRuta("solo-fecha", null, plan.get().getNewDay());
    }
    return null;
  }
}
