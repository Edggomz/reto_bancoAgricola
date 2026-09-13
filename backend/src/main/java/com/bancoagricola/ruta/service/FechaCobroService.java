package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.CatalogoFrecuenciaDia;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.error.ConflictException;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cambiar fecha de cobro (03 -> 04 -> 05, y el canal de voz). Solo se ofrecen días en
 * que ya le pagaron: su pago +3 y +4, nunca el 28. La fecha elegida se valida en el
 * servidor contra ese set; cambiarla no cambia la cuota ni el plazo.
 *
 * Si la primera cuota con la fecha nueva se corre, esos días generan interés una sola
 * vez, y solo se registra si la persona lo aceptó después de oírlo. Después de un
 * cambio, la fecha queda fija fecha.meses_bloqueo meses.
 */
@Service
public class FechaCobroService {
  private final Contexto contexto;
  private final CalendarioPagos calendario;
  private final Repositorios.PlanesFecha planes;
  private final Repositorios.Clientes clientes;
  private final CopyService copy;
  private final AvisoService avisos;
  private final RecordService record;
  private final AuditoriaService auditoria;

  public FechaCobroService(Contexto contexto, CalendarioPagos calendario, Repositorios.PlanesFecha planes,
                           Repositorios.Clientes clientes, CopyService copy, AvisoService avisos, RecordService record, AuditoriaService auditoria) {
    this.contexto = contexto;
    this.calendario = calendario;
    this.planes = planes;
    this.clientes = clientes;
    this.copy = copy;
    this.avisos = avisos;
    this.record = record;
    this.auditoria = auditoria;
  }

  public record Opcion(int dia, LocalDate desde, String nota, int margen, String pago) {}
  public record Grupo(String titulo, List<Opcion> dias) {}
  public record Opciones(int hoy, List<Grupo> grupos, CatalogoFrecuencia frecuencia, Credito credito) {
    public List<Opcion> todas() {
      return grupos.stream().flatMap(g -> g.dias().stream()).toList();
    }

    public Optional<Opcion> buscar(int dia) {
      return todas().stream().filter(o -> o.dia() == dia).findFirst();
    }
  }

  /** Lo que cuesta mover la fecha: días que se corre la primera cuota y su interés (null si el crédito no trae saldo o tasa). */
  public record Costo(LocalDate cobroSinCambio, int diasExtra, BigDecimal interes) {}

  @Transactional(readOnly = true)
  public App.OpcionesFecha opciones(String clienteId, String frecuenciaSlug) {
    Opciones o = calcular(contexto.cargar(clienteId), frecuenciaSlug);
    return aDto(o);
  }

  /** Cada día trae su costo: la persona ve el interés antes de confirmar, no después. */
  public App.OpcionesFecha aDto(Opciones o) {
    List<App.GrupoFecha> grupos = o.grupos().stream()
        .map(g -> new App.GrupoFecha(g.titulo(), g.dias().stream().map(d -> {
          Costo costo = costo(o.credito(), o.hoy(), d.desde());
          return new App.DiaOpcion(d.dia(), "Desde el " + Fechas.etiqueta(d.desde()), d.nota(),
              textoCosto(costo, "fecha.costo.opcion", d.desde()), interes(costo), costo.diasExtra());
        }).toList()))
        .toList();
    return new App.OpcionesFecha(o.hoy(), grupos);
  }

  /** El costo redactado. prefijo: fecha.costo.opcion (antes de confirmar) o fecha.costo.listo (vista 05). */
  public String textoCosto(Costo costo, String prefijo, LocalDate desde) {
    if (costo.interes() == null) return copy.render(prefijo + "_sin_datos", Map.of("dias", costo.diasExtra()));
    if (costo.diasExtra() == 0 || costo.interes().signum() == 0) return copy.texto(prefijo + "_sin_interes");
    return copy.render(prefijo + "_interes",
        Map.of("dias", costo.diasExtra(), "monto", Fechas.monto(costo.interes()), "fecha", Fechas.etiqueta(desde)));
  }

  private static double interes(Costo costo) {
    return costo.interes() == null ? 0 : Fechas.d(costo.interes());
  }

  /** Calcula las opciones para un crédito con cuota, según la frecuencia elegida. */
  public Opciones calcular(Contexto.Datos datos, String frecuenciaSlug) {
    Credito credito = datos.principalConCuota()
        .orElseThrow(() -> new IllegalArgumentException("No tienes un crédito con cuota mensual para mover."));
    CatalogoFrecuencia f = calendario.porSlugOId(frecuenciaSlug);
    LocalDate hoy = LocalDate.now();
    int diaActual = diaActual(datos, credito);
    LocalDate cobroActual = Fechas.desdeDia(hoy, diaActual);
    LocalDate minimo = cobroActual.plusDays(copy.entero("fecha.min_dias_entre_cobros"));
    Set<Integer> excluidos = excluidos();
    List<CatalogoFrecuenciaDia> filas = calendario.dias(f);
    Map<String, List<Opcion>> grupos = new LinkedHashMap<>();

    if (f.isSemanal()) {
      CatalogoFrecuenciaDia fila = filas.isEmpty() ? null : filas.get(0);
      DayOfWeek pago = calendario.diaSemanaPago(f);
      String etiquetaPago = fila == null || fila.getPagoLabel() == null ? "pago semanal" : fila.getPagoLabel();
      String titulo = fila == null || fila.getTitulo() == null ? "DESPUÉS DE TU PAGO SEMANAL" : fila.getTitulo();
      List<Opcion> dias = new ArrayList<>();
      for (LocalDate d = minimo; d.isBefore(minimo.plusDays(35)) && dias.size() < 5; d = d.plusDays(1)) {
        if (d.getDayOfWeek() != pago.plus(f.getOffsetMin())) continue;
        int dia = d.getDayOfMonth();
        if (excluidos.contains(dia) || dias.stream().anyMatch(x -> x.dia() == dia)) continue;
        dias.add(new Opcion(dia, d, nota(dia, f.getOffsetMin(), etiquetaPago), f.getOffsetMin(), etiquetaPago));
      }
      grupos.put(titulo, dias);
    } else if (f.isVariable()) {
      String titulo = copy.texto("fecha.titulo_variable");
      String etiquetaPago = copy.texto("fecha.pago_variable");
      List<Opcion> dias = new ArrayList<>();
      for (Integer base : calendario.diasVariables(datos.creditoIds(), hoy)) {
        for (int offset = f.getOffsetMin(); offset <= f.getOffsetMax(); offset++) {
          int dia = base >= 28 ? offset : base + offset;
          if (dia > 27 || excluidos.contains(dia) || dias.stream().anyMatch(x -> x.dia() == dia)) continue;
          dias.add(new Opcion(dia, Fechas.desdeDia(minimo, dia), copy.texto("fecha.nota_variable"), offset, etiquetaPago));
        }
      }
      grupos.put(titulo, dias);
    } else {
      for (CatalogoFrecuenciaDia fila : filas) {
        int base = fila.isFinDeMes() ? 0 : fila.getDiaPago();
        String etiquetaPago = fila.getPagoLabel() == null ? "pago" : fila.getPagoLabel();
        List<Opcion> dias = new ArrayList<>();
        for (int offset = f.getOffsetMin(); offset <= f.getOffsetMax(); offset++) {
          int dia = base + offset;
          if (dia > 27 || excluidos.contains(dia)) continue;
          dias.add(new Opcion(dia, Fechas.desdeDia(minimo, dia), nota(dia, offset, etiquetaPago), offset, etiquetaPago));
        }
        grupos.put(fila.getTitulo() == null ? "DESPUÉS DE TU PAGO" : fila.getTitulo(), dias);
      }
    }
    List<Grupo> lista = grupos.entrySet().stream().filter(e -> !e.getValue().isEmpty())
        .map(e -> new Grupo(e.getKey(), e.getValue())).toList();
    return new Opciones(diaActual, lista, f, credito);
  }

  /**
   * Opciones a partir de los días en que la persona dijo que le llega el dinero (canal
   * de voz), con la misma regla que calcular: su pago + offset_min, nunca un día
   * excluido y al menos fecha.min_dias_entre_cobros después del cobro actual. Semanal,
   * o sin días, cae en calcular (catálogo y ledger).
   */
  public Opciones calcularPorDias(Contexto.Datos datos, String frecuenciaSlug, List<Integer> diasIngreso, String etiquetaPago) {
    CatalogoFrecuencia f = calendario.porSlugOId(frecuenciaSlug);
    List<Integer> pagos = diasIngreso == null ? List.of()
        : diasIngreso.stream().filter(d -> d != null && d >= 1 && d <= 31).distinct().sorted().toList();
    if (f.isSemanal() || pagos.isEmpty()) return calcular(datos, frecuenciaSlug);
    Credito credito = datos.principalConCuota()
        .orElseThrow(() -> new IllegalArgumentException("No tienes un crédito con cuota mensual para mover."));
    int diaActual = diaActual(datos, credito);
    LocalDate minimo = Fechas.desdeDia(LocalDate.now(), diaActual).plusDays(copy.entero("fecha.min_dias_entre_cobros"));
    Set<Integer> excluidos = excluidos();
    List<Opcion> dias = new ArrayList<>();
    for (int pago : pagos) {
      // Del 28 al 31 es «fin de mes», igual que en CATALOGO_FRECUENCIA_DIA.
      int base = pago >= 28 ? 0 : pago;
      for (int offset = f.getOffsetMin(); offset <= f.getOffsetMax(); offset++) {
        int dia = base + offset;
        // Un pago del 25 al 27 pasa al mes siguiente: el primer día que deja el margen incluso en febrero.
        if (dia > 27) dia = Math.max(1, offset - (28 - base));
        int candidato = dia;
        if (excluidos.contains(dia) || dias.stream().anyMatch(x -> x.dia() == candidato)) continue;
        LocalDate desde = Fechas.desdeDia(minimo, dia);
        YearMonth mesDelPago = base == 0 || dia < base ? YearMonth.from(desde).minusMonths(1) : YearMonth.from(desde);
        int margen = (int) ChronoUnit.DAYS.between(Fechas.conDia(mesDelPago, pago), desde);
        String etiqueta = copy.render(pago >= 28 ? "fecha.pago_fin_de_mes" : "fecha.pago_del_dia", Map.of("pago", etiquetaPago, "dia", pago));
        dias.add(new Opcion(dia, desde, nota(dia, margen, etiqueta), margen, etiqueta));
        break;
      }
    }
    String titulo = copy.render("fecha.titulo_por_dias", Map.of("pago", etiquetaPago.toUpperCase(Locale.ROOT)));
    return new Opciones(diaActual, dias.isEmpty() ? List.of() : List.of(new Grupo(titulo, dias)), f, credito);
  }

  /** Día de cobro vigente: el del plan si ya lo movió; si no, el del crédito. */
  public int diaActual(Contexto.Datos datos, Credito credito) {
    return datos.plan(credito).map(PlanFechaCobro::getNewDay).filter(d -> d > 0).orElse(credito.diaDeCobro());
  }

  /**
   * Cuánto se corre la primera cuota con la fecha nueva, contra la que venía un mes
   * después del cobro actual. Si se adelanta no hay interés extra.
   */
  public Costo costo(Credito credito, int diaActual, LocalDate primerCobroNuevo) {
    LocalDate cobroActual = Fechas.desdeDia(LocalDate.now(), diaActual);
    LocalDate sinCambio = Fechas.conDia(YearMonth.from(cobroActual).plusMonths(1), diaActual);
    int dias = (int) Math.max(0, ChronoUnit.DAYS.between(sinCambio, primerCobroNuevo));
    if (dias == 0) return new Costo(sinCambio, 0, BigDecimal.ZERO);
    if (credito.getSaldoCapital() == null || credito.getTasaAnual() == null) return new Costo(sinCambio, dias, null);
    BigDecimal interes = credito.getSaldoCapital().multiply(credito.getTasaAnual()).multiply(BigDecimal.valueOf(dias))
        .divide(BigDecimal.valueOf(copy.entero("fecha.interes_base_dias")), 2, RoundingMode.HALF_UP);
    return new Costo(sinCambio, dias, interes);
  }

  /** Hasta cuándo no se puede volver a mover la fecha de este crédito; vacío si ya se puede. */
  public Optional<LocalDate> bloqueadoHasta(Contexto.Datos datos, Credito credito) {
    LocalDate hoy = LocalDate.now();
    return datos.plan(credito).map(PlanFechaCobro::getBloqueadoHasta).filter(h -> h.isAfter(hoy));
  }

  private Set<Integer> excluidos() {
    return copy.lista("fecha.dias_excluidos").stream().map(Integer::parseInt).collect(Collectors.toSet());
  }

  private String nota(int dia, int margen, String pago) {
    return copy.render("fecha.nota", Map.of("dia", dia, "dias", margen, "pago", pago));
  }

  /** «¿Qué día te pagan?» se guarda apenas la persona responde: es dato del cliente, no del plan. */
  @Transactional
  public CatalogoFrecuencia guardarFrecuencia(String clienteId, String frecuenciaSlug) {
    CatalogoFrecuencia f = calendario.porSlugOId(frecuenciaSlug);
    Cliente c = contexto.cliente(clienteId);
    c.setFrecuenciaPago(f.getId());
    clientes.save(c);
    return f;
  }

  public record Confirmacion(App.FechaConfirmada dto, PlanFechaCobro plan, Opcion opcion, Credito credito, Costo costo) {}

  /** App (04 → 05) y chat del asesor. aceptaInteres: la persona vio el interés de ese día y confirmó. */
  @Transactional
  public Confirmacion confirmar(String clienteId, String frecuenciaSlug, Integer dia, String creditoId, boolean aceptaInteres) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    return confirmar(datos, calcular(datos, frecuenciaSlug), dia, creditoId, PlanFechaCobro.CANAL_APP, aceptaInteres);
  }

  /**
   * Guarda la fecha nueva. Si la primera cuota se corre, el interés de esos días se
   * muestra antes (vista 04, chat o voz) y la fecha solo cambia si se aceptó; se
   * cobra una sola vez, con esa cuota.
   */
  @Transactional
  public Confirmacion confirmar(Contexto.Datos datos, Opciones opciones, Integer dia, String creditoId,
                                String canal, boolean aceptaInteres) {
    if (dia == null) throw new IllegalArgumentException("Elige un día para confirmar.");
    String clienteId = datos.cliente().getId();
    Opcion elegida = opciones.buscar(dia)
        .orElseThrow(() -> new IllegalArgumentException("Ese día no cae después de tu pago. Elige uno de los que te mostramos."));
    Credito credito = opciones.credito();
    if (creditoId != null && !creditoId.isBlank()) {
      credito = datos.credito(creditoId).filter(Credito::tieneCuota)
          .orElseThrow(() -> new IllegalArgumentException("Ese crédito no tiene cuota mensual para mover."));
    }
    Optional<LocalDate> bloqueo = bloqueadoHasta(datos, credito);
    if (bloqueo.isPresent()) {
      throw new ConflictException(copy.render("fecha.bloqueada", Map.of("fecha", Fechas.etiquetaAnio(bloqueo.get()))));
    }
    Costo costo = costo(credito, opciones.hoy(), elegida.desde());
    boolean porVoz = PlanFechaCobro.CANAL_VOZ.equals(canal);
    boolean cobraInteres = costo.interes() != null && costo.interes().signum() > 0;
    if (costo.diasExtra() > 0 && costo.interes() == null) throw new ConflictException(copy.texto("fecha.interes_sin_datos"));
    if (cobraInteres && !aceptaInteres) throw new IllegalArgumentException(copy.texto("fecha.interes_sin_aceptar"));

    PlanFechaCobro plan = planes.findById(credito.getId()).orElseGet(PlanFechaCobro::new);
    plan.setCreditoId(credito.getId());
    plan.setNewDay(dia);
    plan.setEffectiveFrom(elegida.desde());
    plan.setEffectiveFromLabel(copy.render("fecha.desde", Map.of("fecha", Fechas.etiqueta(elegida.desde()))));
    plan.setAmountUnchanged(true);
    plan.setTermUnchanged(true);
    plan.setFrecuenciaId(opciones.frecuencia().getId());
    plan.setOpcionId("date-" + dia);
    plan.setDiasExtra(costo.diasExtra());
    plan.setInteresExtra(cobraInteres ? costo.interes() : BigDecimal.ZERO);
    plan.setAceptoInteres(cobraInteres);
    plan.setCanal(porVoz ? PlanFechaCobro.CANAL_VOZ : PlanFechaCobro.CANAL_APP);
    plan.setBloqueadoHasta(LocalDate.now().plusMonths(copy.entero("fecha.meses_bloqueo")));
    planes.save(plan);

    Cliente c = datos.cliente();
    c.setFrecuenciaPago(opciones.frecuencia().getId());
    clientes.save(c);
    record.sumar(clienteId, copy.texto("record.sumando.fecha"));

    String cuerpo = cobraInteres
        ? copy.render("aviso.fecha.cuerpo_interes", Map.of("fecha", Fechas.etiqueta(elegida.desde()),
            "monto", Fechas.monto(plan.getInteresExtra()), "dias", costo.diasExtra()))
        : copy.render("aviso.fecha.cuerpo", Map.of("fecha", Fechas.etiqueta(elegida.desde())));
    avisos.emitir(clienteId, AvisoService.CONFIRM, copy.render("aviso.fecha.titulo", Map.of("dia", dia)), cuerpo,
        null, credito.getId(), "fecha-cambiada");
    auditoria.info(porVoz ? AuditoriaService.VOZ : AuditoriaService.APP, "fecha.cambiada", clienteId, credito.getId(),
        "Cobro movido del día " + opciones.hoy() + " al " + dia + ", desde el " + Fechas.etiqueta(elegida.desde())
            + (cobraInteres ? " · " + Fechas.monto(plan.getInteresExtra()) + " de interés por " + costo.diasExtra() + " días" : " · sin interés"),
        Map.of("diaAnterior", opciones.hoy(), "diaNuevo", dia, "desde", elegida.desde().toString(), "diasExtra", costo.diasExtra(),
            "interes", plan.getInteresExtra(), "canal", plan.getCanal(), "bloqueadoHasta", plan.getBloqueadoHasta().toString()));

    String operacion = credito.getOperationNumber() == null ? credito.ultimos4() : credito.getOperationNumber();
    App.FechaConfirmada dto = new App.FechaConfirmada(dia, Fechas.etiquetaAnio(elegida.desde()), operacion,
        textoCosto(costo, "fecha.costo.listo", elegida.desde()), interes(costo), costo.diasExtra());
    return new Confirmacion(dto, plan, elegida, credito, costo);
  }
}
