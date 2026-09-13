package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.domain.RecordHito;
import com.bancoagricola.ruta.domain.RecordPago;
import com.bancoagricola.ruta.domain.RecordSumando;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mi récord (12): lo que suma y lo que viene. Nada aparece como castigo. */
@Service
public class RecordService {
  private static final Pattern NUMERO = Pattern.compile("(\\d+)");

  private final Contexto contexto;
  private final Repositorios.Records records;
  private final Repositorios.Hitos hitos;
  private final Repositorios.Sumandos sumandos;
  private final CopyService copy;

  public RecordService(Contexto contexto, Repositorios.Records records, Repositorios.Hitos hitos,
                       Repositorios.Sumandos sumandos, CopyService copy) {
    this.contexto = contexto;
    this.records = records;
    this.hitos = hitos;
    this.sumandos = sumandos;
    this.copy = copy;
  }

  @Transactional(readOnly = true)
  public App.Record record(String clienteId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    RecordPago r = records.findById(clienteId).orElseGet(() -> vacio(clienteId));
    List<App.HitoRecord> lista = new ArrayList<>();
    lista.add(new App.HitoRecord(r.getProgressCurrent(), "Hoy", true));
    List<String> salidas = new ArrayList<>();
    int siguiente = r.getProgressCurrent();
    for (RecordHito h : hitos.findByClienteIdOrderByIdxAsc(clienteId)) {
      Matcher m = NUMERO.matcher(h.getLabel());
      int n = m.find() ? Integer.parseInt(m.group(1)) : siguiente + 1;
      siguiente = Math.max(siguiente, n);
      lista.add(new App.HitoRecord(n, etiquetaCorta(h.getDateLabel()), false));
      if (h.getLabel().toLowerCase().contains("sale") || n == r.getProgressTotal() || n == r.getProgressTotal() - 1) {
        salidas.add(etiquetaCorta(h.getDateLabel()).toLowerCase());
      }
    }
    String explicacion = salidas.isEmpty() ? copy.texto("record.explicacion")
        : copy.render("record.explicacion.salida", Map.of("fechas", Fechas.enumerar(salidas)));

    List<App.Suma> suma = new ArrayList<>();
    for (RecordSumando s : sumandos.findByClienteIdOrderByOrdenAsc(clienteId)) {
      suma.add(new App.Suma(s.getLabel(), detalle(s.getLabel(), datos)));
    }
    if (datos.rutaActiva() && suma.stream().noneMatch(s -> s.titulo().toLowerCase().contains("apartas"))) {
      suma.add(new App.Suma(copy.texto("record.sumando.apartas"), copy.texto("record.suma.detalle.apartas")));
    }
    datos.creditos().stream().filter(c -> c.esTarjeta() && c.getUsedPct() != null && c.getUsedPct() < 50).findFirst()
        .filter(c -> suma.stream().noneMatch(s -> s.titulo().contains("línea")))
        .ifPresent(c -> suma.add(new App.Suma("Usas el " + c.getUsedPct() + " % de tu línea",
            copy.render("record.suma.detalle.linea", Map.of("pct", c.getUsedPct())))));

    return new App.Record(r.getStreakMonths(), proximo(datos), lista, explicacion, suma);
  }

  /** Resumen para el inicio: racha y próximo +1. */
  @Transactional(readOnly = true)
  public App.RecordResumen resumen(Contexto.Datos datos) {
    RecordPago r = records.findById(datos.cliente().getId()).orElseGet(() -> vacio(datos.cliente().getId()));
    return new App.RecordResumen(r.getStreakMonths(), proximo(datos));
  }

  /** El próximo +1 es la próxima cuota: con la fecha nueva si ya la movió. */
  public String proximo(Contexto.Datos datos) {
    Optional<Credito> principal = datos.principalConCuota().or(() -> datos.principalApartable());
    if (principal.isEmpty()) return Fechas.etiqueta(LocalDate.now().plusMonths(1));
    Credito c = principal.get();
    int dia = datos.plan(c).map(PlanFechaCobro::getNewDay).filter(d -> d > 0).orElse(c.diaDeCobro());
    LocalDate desde = datos.plan(c).map(PlanFechaCobro::getEffectiveFrom).orElse(LocalDate.now());
    LocalDate fecha = Fechas.desdeDia(desde.isAfter(LocalDate.now()) ? desde : LocalDate.now().plusDays(1), dia);
    return Fechas.etiqueta(fecha);
  }

  /** Suma un mes al récord cuando una cuota se paga a tiempo. */
  @Transactional
  public RecordPago registrarPagoATiempo(String clienteId) {
    RecordPago r = records.findById(clienteId).orElseGet(() -> vacio(clienteId));
    r.setStreakMonths(r.getStreakMonths() + 1);
    r.setProgressCurrent(Math.min(r.getProgressTotal(), r.getProgressCurrent() + 1));
    r.setNextPlusOneLabel(copy.render("record.proximo", Map.of("fecha", Fechas.etiqueta(LocalDate.now().plusMonths(1)))));
    return records.save(r);
  }

  /** Agrega una fila a «qué te está sumando» si aún no estaba. */
  @Transactional
  public void sumar(String clienteId, String label) {
    List<RecordSumando> actuales = sumandos.findByClienteIdOrderByOrdenAsc(clienteId);
    if (actuales.stream().anyMatch(s -> s.getLabel().equalsIgnoreCase(label))) return;
    RecordSumando s = new RecordSumando();
    s.setId("sum-" + UUID.randomUUID());
    s.setClienteId(clienteId);
    s.setLabel(label);
    s.setOrden(actuales.size() + 1);
    sumandos.save(s);
  }

  private RecordPago vacio(String clienteId) {
    RecordPago r = new RecordPago();
    r.setClienteId(clienteId);
    r.setStreakMonths(0);
    r.setProgressCurrent(0);
    r.setProgressTotal(24);
    r.setNextPlusOneLabel("");
    r.setConsultsNote("");
    return r;
  }

  private String detalle(String label, Contexto.Datos datos) {
    String l = label.toLowerCase();
    if (l.contains("apartas") || l.contains("apartaste")) return copy.texto("record.suma.detalle.apartas");
    if (l.contains("línea") || l.contains("linea")) {
      Integer pct = datos.creditos().stream().filter(Credito::esTarjeta).map(Credito::getUsedPct).filter(p -> p != null).findFirst().orElse(0);
      return copy.render("record.suma.detalle.linea", Map.of("pct", pct));
    }
    return copy.texto("record.suma.detalle.generico");
  }

  /** «23 feb 2027» -> «Feb 2027»; «Mañana» se deja igual. */
  private static String etiquetaCorta(String dateLabel) {
    if (dateLabel == null) return "";
    String[] partes = dateLabel.trim().split("\\s+");
    if (partes.length >= 3 && partes[0].matches("\\d+")) {
      String mes = partes[1];
      return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + partes[2];
    }
    return dateLabel;
  }
}
