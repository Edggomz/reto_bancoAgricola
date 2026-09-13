package com.bancoagricola.ruta.ai;

import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.Cuenta;
import com.bancoagricola.ruta.dto.Api;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.service.ApartadoService;
import com.bancoagricola.ruta.service.CalendarioPagos;
import com.bancoagricola.ruta.service.Contexto;
import com.bancoagricola.ruta.service.CopyService;
import com.bancoagricola.ruta.service.FechaCobroService;
import com.bancoagricola.ruta.service.Fechas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IA del FORMULARIO: recomienda la opción más conveniente de cada pantalla de
 * opciones (03 ¿qué día te pagan?, 04 ¿qué día te queda mejor?, 07 ¿en cuántas
 * partes?). La persona sigue decidiendo: la app solo pre-resalta.
 *
 * Guardrail: la opción recomendada DEBE existir en el set; si no, o si no hay
 * modelo, cae en una heurística con los datos reales (ledger de abonos, saldo,
 * márgenes). Nunca inventa opciones ni datos.
 */
@Service
public class FormAiService {
  private final LlmRouter router;
  private final Guardrails guardrails;
  private final ObjectMapper mapper;
  private final Contexto contexto;
  private final CalendarioPagos calendario;
  private final FechaCobroService fechaCobro;
  private final ApartadoService apartado;
  private final CopyService copy;

  public FormAiService(LlmRouter router, Guardrails guardrails, ObjectMapper mapper, Contexto contexto,
                       CalendarioPagos calendario, FechaCobroService fechaCobro, ApartadoService apartado, CopyService copy) {
    this.router = router;
    this.guardrails = guardrails;
    this.mapper = mapper;
    this.contexto = contexto;
    this.calendario = calendario;
    this.fechaCobro = fechaCobro;
    this.apartado = apartado;
    this.copy = copy;
  }

  // --- 03 · ¿Qué día te pagan? -------------------------------------------------
  @Transactional(readOnly = true)
  public App.Sugerencia frecuencia(String clienteId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    CalendarioPagos.Patron patron = calendario.patron(datos.creditoIds(), LocalDate.now());
    List<CatalogoFrecuencia> todas = calendario.todas();
    String heuristica = patron.abonosPorMes() >= 3.5 ? "semanal"
        : patron.picoQuincena() && patron.picoFinDeMes() ? "quincena-fin-de-mes"
        : patron.picoFinDeMes() && !patron.picoQuincena() ? "fin-de-mes"
        : patron.abonos() < 2 ? "quincena-fin-de-mes" : "variable";
    String patronTexto = switch (heuristica) {
      case "semanal" -> "cada semana";
      case "fin-de-mes" -> "una vez al mes, a fin de mes";
      case "quincena-fin-de-mes" -> "cada quincena y a fin de mes";
      default -> "en días distintos cada mes";
    };
    Map<String, String> opciones = new LinkedHashMap<>();
    for (CatalogoFrecuencia f : todas) opciones.put(f.getSlug(), f.getLabel());
    String contexto = "Abonos del cliente en los últimos 90 días: " + patron.abonos() + " (" + String.format("%.1f", patron.abonosPorMes())
        + " por mes). Días del mes con más abonos: " + patron.dias() + " (28 = fin de mes). Pico de quincena: " + patron.picoQuincena()
        + ". Pico de fin de mes: " + patron.picoFinDeMes() + ".";
    return elegir("sugerencia", contexto, opciones, heuristica,
        copy.render("frecuencia.sugerencia", Map.of("patron", patronTexto, "frecuencia", opciones.getOrDefault(heuristica, heuristica))));
  }

  // --- 04 · ¿Qué día te queda mejor? ------------------------------------------
  @Transactional(readOnly = true)
  public App.Sugerencia fecha(String clienteId, String frecuenciaSlug) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    FechaCobroService.Opciones o = fechaCobro.calcular(datos, frecuenciaSlug);
    List<FechaCobroService.Opcion> todas = o.todas();
    if (todas.isEmpty()) return new App.Sugerencia(null, "", 0, "heuristica");
    FechaCobroService.Opcion mejor = todas.stream()
        .max(Comparator.comparingInt(FechaCobroService.Opcion::margen).thenComparing(FechaCobroService.Opcion::desde, Comparator.reverseOrder()))
        .orElse(todas.get(0));
    Map<String, String> opciones = new LinkedHashMap<>();
    for (FechaCobroService.Opcion op : todas) {
      opciones.put(String.valueOf(op.dia()), "Día " + op.dia() + " · " + op.margen() + " días después de su " + op.pago()
          + " · aplica desde el " + Fechas.etiqueta(op.desde()));
    }
    String contexto = "Cliente: " + datos.cliente().getFirstName() + ". Cuota mensual: " + datos.principalConCuota().map(c -> Fechas.monto(c.getInstallmentAmount())).orElse("-")
        + ". Saldo disponible: " + datos.origen().map(c -> Fechas.monto(c.getBalanceAvailable())).orElse("sin cuenta")
        + ". Le pagan: " + o.frecuencia().getLabel() + ". Hoy su cobro es el día " + o.hoy() + ".";
    return elegir("sugerencia", contexto, opciones, String.valueOf(mejor.dia()),
        copy.render("fecha.sugerencia", Map.of("dia", mejor.dia(), "dias", mejor.margen(), "pago", mejor.pago())));
  }

  // --- 07 · ¿En cuántas partes? -----------------------------------------------
  @Transactional(readOnly = true)
  public App.Sugerencia partes(String clienteId, String creditoId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Credito c = apartado.creditoApartable(datos, creditoId);
    CatalogoFrecuencia f = calendario.delClienteODefault(datos);
    List<Integer> permitidas = f.partes();
    BigDecimal saldo = datos.origen().map(Cuenta::getBalanceAvailable).orElse(BigDecimal.ZERO);
    int heuristica = permitidas.contains(f.getPartesSugeridas()) ? f.getPartesSugeridas() : permitidas.get(0);
    Map<String, String> opciones = new LinkedHashMap<>();
    for (Integer n : permitidas) {
      ApartadoService.Plan plan = apartado.plan(datos, c, n);
      BigDecimal primera = plan.cuotas().get(0).monto();
      if (n == heuristica && saldo.signum() > 0 && primera.compareTo(saldo) > 0) heuristica = permitidas.get(permitidas.size() - 1);
      opciones.put(String.valueOf(n), (n == 1 ? "1 parte" : n + " partes") + " de " + Fechas.monto(primera) + " · fechas: "
          + Fechas.enumerarDias(plan.fechas()) + " · se paga el " + Fechas.etiqueta(plan.fechaPago()));
    }
    String etiqueta = heuristica == 1 ? "1 parte" : heuristica + " partes";
    String contexto = "Cliente: " + datos.cliente().getFirstName() + ". Le pagan: " + f.getLabel() + ". Cuota a apartar: "
        + Fechas.monto(c.montoApartable()) + ". Saldo disponible hoy: " + Fechas.monto(saldo) + ".";
    return elegir("sugerencia", contexto, opciones, String.valueOf(heuristica),
        copy.render("apartado.sugerencia", Map.of("partes", etiqueta)));
  }

  /** Pide al modelo elegir una opción; si falla o inventa, usa la heurística. */
  private App.Sugerencia elegir(String servicio, String contexto, Map<String, String> opciones, String heuristica, String motivoHeuristico) {
    if (router.disponible() && opciones.size() > 1) {
      try {
        StringBuilder user = new StringBuilder("Contexto del cliente:\n").append(contexto).append("\n\nOpciones (id: descripción):\n");
        opciones.forEach((id, desc) -> user.append("- ").append(id).append(": ").append(desc).append('\n'));
        user.append("\nElige el id más conveniente para el cliente.");
        Optional<LlmRouter.Respuesta> r = router.generar(new LlmRouter.Solicitud(servicio, SystemPrompts.FORM_SUGGEST, user.toString(),
            true, true, 300, 0.2, null));
        if (r.isPresent()) {
          Optional<JsonNode> n = router.json(r.get().texto());
          if (n.isPresent()) {
            String id = n.get().path("opcion").asText(null);
            if (guardrails.opcionPermitida(id, opciones.keySet())) {
              String motivo = guardrails.sanitize(n.get().path("motivo").asText(""));
              double conf = n.get().path("confianza").asDouble(0.7);
              return new App.Sugerencia(id, motivo == null || motivo.isBlank() ? motivoHeuristico : motivo,
                  Math.max(0, Math.min(1, conf)), "modelo:" + r.get().proveedor());
            }
          }
        }
      } catch (Exception ignored) {
        // cae a la heurística
      }
    }
    return new App.Sugerencia(heuristica, motivoHeuristico, 0.5, "heuristica");
  }

  /** Endpoint genérico (aditivo): recomienda una opción de cualquier formulario. */
  public Api.FormSuggestResponse suggest(Api.FormSuggestRequest req) {
    List<Api.FormOption> options = req == null || req.options() == null ? List.of() : req.options();
    List<String> ids = new ArrayList<>(options.stream().map(Api.FormOption::id).toList());
    if (ids.isEmpty()) return new Api.FormSuggestResponse(null, copy.texto("form.sin_opciones"), 0.0);
    Map<String, String> opciones = new LinkedHashMap<>();
    for (Api.FormOption o : options) opciones.put(o.id(), o.label() + (o.meta() == null ? "" : " " + o.meta()));
    String ctx;
    try {
      ctx = mapper.writeValueAsString(req.context() == null ? Map.of() : req.context());
    } catch (Exception e) {
      ctx = String.valueOf(req.context());
    }
    App.Sugerencia s = elegir("form", ctx, opciones, ids.get(0), copy.texto("form.razon_heuristica"));
    return new Api.FormSuggestResponse(s.opcion(), s.motivo(), s.confianza());
  }
}
