package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.CatalogoFrecuenciaDia;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cambiar fecha de cobro (03 -> 04 -> 05). Solo se ofrecen días en que ya le
 * pagaron: su pago +3 y +4, nunca el 28. La fecha elegida se valida en el
 * servidor contra ese set; cambiarla no cambia monto ni plazo.
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

  public FechaCobroService(Contexto contexto, CalendarioPagos calendario, Repositorios.PlanesFecha planes,
                           Repositorios.Clientes clientes, CopyService copy, AvisoService avisos, RecordService record) {
    this.contexto = contexto;
    this.calendario = calendario;
    this.planes = planes;
    this.clientes = clientes;
    this.copy = copy;
    this.avisos = avisos;
    this.record = record;
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

  @Transactional(readOnly = true)
  public App.OpcionesFecha opciones(String clienteId, String frecuenciaSlug) {
    Opciones o = calcular(contexto.cargar(clienteId), frecuenciaSlug);
    return aDto(o);
  }

  public static App.OpcionesFecha aDto(Opciones o) {
    List<App.GrupoFecha> grupos = o.grupos().stream()
        .map(g -> new App.GrupoFecha(g.titulo(), g.dias().stream()
            .map(d -> new App.DiaOpcion(d.dia(), "Desde el " + Fechas.etiqueta(d.desde()), d.nota())).toList()))
        .toList();
    return new App.OpcionesFecha(o.hoy(), grupos);
  }

  /** Calcula las opciones para un crédito con cuota, según la frecuencia elegida. */
  public Opciones calcular(Contexto.Datos datos, String frecuenciaSlug) {
    Credito credito = datos.principalConCuota()
        .orElseThrow(() -> new IllegalArgumentException("No tienes un crédito con cuota mensual para mover."));
    CatalogoFrecuencia f = calendario.porSlugOId(frecuenciaSlug);
    LocalDate hoy = LocalDate.now();
    int diaActual = datos.plan(credito).map(PlanFechaCobro::getNewDay).filter(d -> d > 0).orElse(credito.diaDeCobro());
    LocalDate cobroActual = Fechas.desdeDia(hoy, diaActual);
    LocalDate minimo = cobroActual.plusDays(copy.entero("fecha.min_dias_entre_cobros"));
    Set<Integer> excluidos = copy.lista("fecha.dias_excluidos").stream().map(Integer::parseInt).collect(Collectors.toSet());
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

  public record Confirmacion(App.FechaConfirmada dto, PlanFechaCobro plan, Opcion opcion, Credito credito) {}

  @Transactional
  public Confirmacion confirmar(String clienteId, String frecuenciaSlug, Integer dia, String creditoId) {
    if (dia == null) throw new IllegalArgumentException("Elige un día para confirmar.");
    Contexto.Datos datos = contexto.cargar(clienteId);
    Opciones opciones = calcular(datos, frecuenciaSlug);
    Opcion elegida = opciones.buscar(dia)
        .orElseThrow(() -> new IllegalArgumentException("Ese día no cae después de tu pago. Elige uno de los que te mostramos."));
    Credito credito = opciones.credito();
    if (creditoId != null && !creditoId.isBlank()) {
      credito = datos.credito(creditoId).filter(Credito::tieneCuota)
          .orElseThrow(() -> new IllegalArgumentException("Ese crédito no tiene cuota mensual para mover."));
    }
    PlanFechaCobro plan = planes.findById(credito.getId()).orElseGet(PlanFechaCobro::new);
    plan.setCreditoId(credito.getId());
    plan.setNewDay(dia);
    plan.setEffectiveFrom(elegida.desde());
    plan.setEffectiveFromLabel(copy.render("fecha.desde", Map.of("fecha", Fechas.etiqueta(elegida.desde()))));
    plan.setAmountUnchanged(true);
    plan.setTermUnchanged(true);
    plan.setFrecuenciaId(opciones.frecuencia().getId());
    plan.setOpcionId("date-" + dia);
    planes.save(plan);

    Cliente c = datos.cliente();
    c.setFrecuenciaPago(opciones.frecuencia().getId());
    clientes.save(c);
    record.sumar(clienteId, copy.texto("record.sumando.fecha"));

    avisos.emitir(clienteId, AvisoService.CONFIRM,
        copy.render("aviso.fecha.titulo", Map.of("dia", dia)),
        copy.render("aviso.fecha.cuerpo", Map.of("fecha", Fechas.etiqueta(elegida.desde()))),
        null, credito.getId(), "fecha-cambiada");

    String operacion = credito.getOperationNumber() == null ? credito.ultimos4() : credito.getOperationNumber();
    App.FechaConfirmada dto = new App.FechaConfirmada(dia, Fechas.etiquetaAnio(elegida.desde()), operacion);
    return new Confirmacion(dto, plan, elegida, credito);
  }
}
