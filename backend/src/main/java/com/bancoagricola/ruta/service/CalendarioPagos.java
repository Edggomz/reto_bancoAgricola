package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.CatalogoFrecuenciaDia;
import com.bancoagricola.ruta.domain.Transaccion;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cuándo le pagan a la persona. Toda la lógica de fechas de cobro y de partes
 * parte de aquí: la regla es que el banco cobre después de que le pagaron, y
 * que le pida tantas partes como veces recibe dinero.
 */
@Service
public class CalendarioPagos {
  private final Repositorios.Frecuencias frecuencias;
  private final Repositorios.FrecuenciaDias frecuenciaDias;
  private final Repositorios.Transacciones transacciones;
  private final CopyService copy;

  public CalendarioPagos(Repositorios.Frecuencias frecuencias, Repositorios.FrecuenciaDias frecuenciaDias,
                         Repositorios.Transacciones transacciones, CopyService copy) {
    this.frecuencias = frecuencias;
    this.frecuenciaDias = frecuenciaDias;
    this.transacciones = transacciones;
    this.copy = copy;
  }

  public CatalogoFrecuencia porSlugOId(String slugOId) {
    if (slugOId == null || slugOId.isBlank()) {
      throw new IllegalArgumentException("Dinos qué día te pagan para seguir.");
    }
    return frecuencias.findBySlug(slugOId.trim()).or(() -> frecuencias.findById(slugOId.trim()))
        .filter(CatalogoFrecuencia::isActivo)
        .orElseThrow(() -> new IllegalArgumentException("No reconocemos esa forma de pago."));
  }

  public List<CatalogoFrecuencia> todas() {
    return frecuencias.findByActivoTrueOrderByOrdenAsc();
  }

  /** La frecuencia que la persona declaró (03) o la de su plan vigente; vacío si aún no la sabemos. */
  public Optional<CatalogoFrecuencia> delCliente(Contexto.Datos datos) {
    String id = datos.cliente().getFrecuenciaPago();
    if (id == null) id = datos.plan().map(p -> p.getFrecuenciaId()).orElse(null);
    if (id == null) return Optional.empty();
    return frecuencias.findById(id);
  }

  public CatalogoFrecuencia delClienteODefault(Contexto.Datos datos) {
    return delCliente(datos).orElseGet(() -> porSlugOId(copy.texto("fecha.frecuencia_default")));
  }

  public List<CatalogoFrecuenciaDia> dias(CatalogoFrecuencia f) {
    return frecuenciaDias.findByFrecuenciaIdOrderByIdxAsc(f.getId());
  }

  public DayOfWeek diaSemanaPago(CatalogoFrecuencia f) {
    return dias(f).stream().map(CatalogoFrecuenciaDia::getDiaSemana).filter(d -> d != null).findFirst()
        .map(Fechas::diaSemana).orElseGet(() -> Fechas.diaSemana(copy.texto("fecha.semanal_dia_pago")));
  }

  /** Cómo entran los abonos de la persona (últimos 90 días del ledger): base de «Es variable» y de la sugerencia. */
  public record Patron(double abonosPorMes, boolean picoQuincena, boolean picoFinDeMes, List<Integer> dias, int abonos) {}

  public Patron patron(List<String> creditoIds, LocalDate hoy) {
    if (creditoIds.isEmpty()) return new Patron(0, false, false, List.of(), 0);
    List<Transaccion> abonos = transacciones.findByCreditoIdInAndFechaAfterOrderByFechaAsc(creditoIds, hoy.minusDays(90))
        .stream().filter(t -> "H".equals(t.getTipo())).toList();
    Map<Integer, Integer> porDia = new TreeMap<>();
    int quincena = 0;
    int fin = 0;
    for (Transaccion t : abonos) {
      int d = t.getFecha().getDayOfMonth();
      int clave = d >= 28 ? 28 : d;
      porDia.merge(clave, 1, Integer::sum);
      if (d >= 13 && d <= 17) quincena++;
      if (d >= 28 || d <= 2) fin++;
    }
    List<Integer> top = porDia.entrySet().stream()
        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
        .limit(2).map(Map.Entry::getKey).sorted().toList();
    double porMes = abonos.size() / 3.0;
    return new Patron(porMes, quincena >= 2, fin >= 2, top, abonos.size());
  }

  /** Días del mes en que suele entrar dinero (28 = fin de mes). Sin historial: quincena y fin de mes. */
  public List<Integer> diasVariables(List<String> creditoIds, LocalDate hoy) {
    List<Integer> dias = patron(creditoIds, hoy).dias();
    return dias.isEmpty() ? List.of(15, 28) : dias;
  }

  /** Fechas de pago de la persona dentro de [desde, hasta]. */
  public List<LocalDate> pagos(CatalogoFrecuencia f, List<Integer> diasVariables, LocalDate desde, LocalDate hasta) {
    TreeSet<LocalDate> out = new TreeSet<>();
    if (f.isSemanal()) {
      DayOfWeek dia = diaSemanaPago(f);
      for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
        if (d.getDayOfWeek() == dia) out.add(d);
      }
      return new ArrayList<>(out);
    }
    List<Integer> diasMes = new ArrayList<>();
    if (f.isVariable()) {
      diasMes.addAll(diasVariables);
    } else {
      for (CatalogoFrecuenciaDia r : dias(f)) {
        diasMes.add(r.isFinDeMes() ? 28 : r.getDiaPago());
      }
    }
    YearMonth m = YearMonth.from(desde).minusMonths(1);
    YearMonth ultimo = YearMonth.from(hasta).plusMonths(1);
    while (!m.isAfter(ultimo)) {
      for (Integer dia : diasMes) {
        LocalDate fecha = dia >= 28 ? m.atEndOfMonth() : Fechas.conDia(m, dia);
        if (!fecha.isBefore(desde) && !fecha.isAfter(hasta)) out.add(fecha);
      }
      m = m.plusMonths(1);
    }
    return new ArrayList<>(out);
  }

  /** Último día de pago estrictamente anterior a {@code fecha}. */
  public LocalDate ultimoPagoAntes(CatalogoFrecuencia f, List<Integer> diasVariables, LocalDate fecha) {
    List<LocalDate> pagos = pagos(f, diasVariables, fecha.minusDays(45), fecha.minusDays(1));
    return pagos.stream().max(Comparator.naturalOrder()).orElse(fecha.minusDays(3));
  }

  /** Próximo corte (>= fecha de referencia), misma regla que FN_CORTE_SIGUIENTE. */
  public static LocalDate corteSiguiente(int diaCorte, LocalDate ref) {
    LocalDate corte = Fechas.conDia(YearMonth.from(ref), diaCorte);
    return corte.isBefore(ref) ? Fechas.conDia(YearMonth.from(ref).plusMonths(1), diaCorte) : corte;
  }

  public static LocalDate corteAnterior(int diaCorte, LocalDate ref) {
    return corteSiguiente(diaCorte, ref).minusMonths(1);
  }
}
