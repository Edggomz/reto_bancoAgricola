package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.ai.Guardrails;
import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.LlamadaVoz;
import com.bancoagricola.ruta.domain.PerfilIngreso;
import com.bancoagricola.ruta.domain.PlanFechaCobro;
import com.bancoagricola.ruta.domain.Sucursal;
import com.bancoagricola.ruta.dto.Voz;
import com.bancoagricola.ruta.error.ConflictException;
import com.bancoagricola.ruta.error.NotFoundException;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Canal de voz: el flujo 03 -> 05 dicho por teléfono, para quien no usa la app. La
 * llamada no recuerda pagar: pregunta cuándo le llega el dinero y mueve el cobro a
 * después. Todo lo que se decide, se decide aquí (a quién se llama, en qué horario,
 * qué fechas se ofrecen, cuánto cuesta y qué quedó); n8n solo traduce entre el
 * proveedor de voz y estos métodos.
 */
@Service
public class VozService {
  private static final Set<String> TIPOS_INGRESO = Set.of("salario", "pension", "remesa", "negocio", "otro");
  private static final Set<String> CANALES_PAGO = Set.of("app", "agencia", "otro");
  /** Lo que el modelo puede reportar. «fecha_cambiada» solo lo pone confirmar(). */
  private static final Set<String> RESULTADOS_DEL_MODELO = Set.of(LlamadaVoz.SIN_CAMBIO, LlamadaVoz.BLOQUEADO,
      LlamadaVoz.TERCERO, LlamadaVoz.VOLVER_A_LLAMAR, LlamadaVoz.NO_LLAMAR);
  /** Con estos resultados ya hubo conversación: no se vuelve a llamar mientras dure el bloqueo. */
  private static final Set<String> CONVERSADAS = Set.of(LlamadaVoz.FECHA_CAMBIADA, LlamadaVoz.SIN_CAMBIO, LlamadaVoz.BLOQUEADO);
  private static final String[] DIAS_SEMANA = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};

  private final Contexto contexto;
  private final FechaCobroService fechaCobro;
  private final CalendarioPagos calendario;
  private final CopyService copy;
  private final Repositorios.Clientes clientes;
  private final Repositorios.PerfilesIngreso perfiles;
  private final Repositorios.LlamadasVoz llamadas;
  private final Repositorios.Sucursales sucursales;
  private final Repositorios.Dispositivos dispositivos;
  private final RutaProperties props;
  private final AuditoriaService auditoria;

  public VozService(Contexto contexto, FechaCobroService fechaCobro, CalendarioPagos calendario, CopyService copy,
                    Repositorios.Clientes clientes, Repositorios.PerfilesIngreso perfiles, Repositorios.LlamadasVoz llamadas,
                    Repositorios.Sucursales sucursales, Repositorios.Dispositivos dispositivos, RutaProperties props, AuditoriaService auditoria) {
    this.contexto = contexto;
    this.fechaCobro = fechaCobro;
    this.calendario = calendario;
    this.copy = copy;
    this.clientes = clientes;
    this.perfiles = perfiles;
    this.llamadas = llamadas;
    this.sucursales = sucursales;
    this.dispositivos = dispositivos;
    this.props = props;
    this.auditoria = auditoria;
  }

  private record Evaluacion(String motivo, Contexto.Datos datos, Credito credito, int diasParaCobro, int intentos) {
    boolean llamable() {
      return motivo == null;
    }

    static Evaluacion no(String motivo) {
      return new Evaluacion(motivo, null, null, 0, 0);
    }
  }

  private record Ingreso(String tipo, String frecuencia, List<Integer> dias, String etiqueta, Boolean constante) {}

  // --- Ventana y elegibilidad --------------------------------------------------

  public ZonedDateTime ahora() {
    return ZonedDateTime.now(ZoneId.of(props.push().zona()));
  }

  /** LPC art. 18 lit. n: de lunes a viernes, de 8:00 a 18:00. La llamada tiene que caber completa. */
  public boolean ventanaAbierta(ZonedDateTime ahora) {
    String dia = DIAS_SEMANA[ahora.getDayOfWeek().getValue() - 1];
    if (!copy.lista("voz.ventana_dias").contains(dia)) return false;
    LocalTime inicio = LocalTime.parse(copy.texto("voz.ventana_inicio"));
    LocalTime ultimaLlamada = LocalTime.parse(copy.texto("voz.ventana_fin")).minusSeconds(copy.entero("voz.duracion_max_seg"));
    LocalTime hora = ahora.toLocalTime();
    return !hora.isBefore(inicio) && !hora.isAfter(ultimaLlamada);
  }

  private Evaluacion evaluar(Cliente c, LocalDate hoy) {
    if (c.getTelefono() == null || c.getTelefono().isBlank()) return Evaluacion.no("no tiene teléfono registrado");
    if (!copy.lista("voz.categorias").contains(c.getCategoria())) return Evaluacion.no("su categoría no entra al canal de voz");
    Contexto.Datos datos = contexto.cargar(c.getId());
    Optional<Credito> principal = datos.principalConCuota();
    if (principal.isEmpty()) return Evaluacion.no("no tiene crédito con cuota");
    Credito credito = principal.get();
    if (!Credito.AL_DIA.equals(credito.getEstadoPago())) return Evaluacion.no("tiene una parte pendiente");
    if (fechaCobro.bloqueadoHasta(datos, credito).isPresent()) return Evaluacion.no("su fecha se cambió hace poco");
    int diaActual = fechaCobro.diaActual(datos, credito);
    int diasParaCobro = (int) ChronoUnit.DAYS.between(hoy, Fechas.desdeDia(hoy, diaActual));
    if (diasParaCobro <= copy.entero("voz.dias_min_antes_cobro")) return Evaluacion.no("está muy cerca de su cobro");

    List<LlamadaVoz> historial = llamadas.findByClienteIdOrderByCreatedAtDesc(c.getId());
    if (historial.stream().anyMatch(l -> LlamadaVoz.NO_LLAMAR.equals(l.getResultado()))) return Evaluacion.no("pidió no recibir llamadas");
    LocalDateTime desdeBloqueo = hoy.minusMonths(copy.entero("fecha.meses_bloqueo")).atStartOfDay();
    if (historial.stream().anyMatch(l -> CONVERSADAS.contains(l.getResultado()) && l.getCreatedAt().isAfter(desdeBloqueo))) {
      return Evaluacion.no("ya conversó con el agente");
    }
    LocalDateTime desdeIntentos = hoy.minusDays(copy.entero("voz.dias_ventana_intentos")).atStartOfDay();
    int intentos = (int) historial.stream().filter(l -> l.getCreatedAt().isAfter(desdeIntentos)).count();
    if (intentos >= copy.entero("voz.max_intentos")) return Evaluacion.no("ya se intentó las veces permitidas");
    LocalDateTime desdeUltimo = hoy.minusDays(copy.entero("voz.dias_entre_intentos") - 1L).atStartOfDay();
    if (!historial.isEmpty() && historial.get(0).getCreatedAt().isAfter(desdeUltimo)) return Evaluacion.no("ya se le llamó hoy");
    return new Evaluacion(null, datos, credito, diasParaCobro, intentos);
  }

  /** Demo: solo exige teléfono y crédito con cuota. Se salta horario, categoría e intentos. */
  private Evaluacion evaluarDemo(Cliente c) {
    if (c.getTelefono() == null || c.getTelefono().isBlank()) return Evaluacion.no("no tiene teléfono registrado");
    Contexto.Datos datos = contexto.cargar(c.getId());
    return datos.principalConCuota().map(cr -> new Evaluacion(null, datos, cr, 0, 0))
        .orElseGet(() -> Evaluacion.no("no tiene crédito con cuota"));
  }

  @Transactional(readOnly = true)
  public Voz.Pendientes pendientes(int limite) {
    ZonedDateTime ahora = ahora();
    LocalDate hoy = ahora.toLocalDate();
    List<Voz.Pendiente> lista = clientes.findByActivoTrue().stream()
        .map(c -> Map.entry(c, evaluar(c, hoy)))
        .filter(e -> e.getValue().llamable())
        .map(e -> new Voz.Pendiente(e.getKey().getId(), e.getKey().getFirstName(), e.getValue().diasParaCobro(),
            usaApp(e.getKey()), e.getValue().intentos()))
        // Primero quien no usa la app: para esa persona existe este canal.
        .sorted(Comparator.comparing(Voz.Pendiente::usaApp).thenComparing(Voz.Pendiente::diasParaCobro))
        .limit(Math.max(0, limite))
        .toList();
    return new Voz.Pendientes(ventanaAbierta(ahora), copy.texto("voz.ventana_descripcion"), lista);
  }

  /** Registra el intento y devuelve lo que el agente necesita saber antes de marcar. */
  @Transactional
  public Voz.Programada programar(String clienteId, boolean demo) {
    if (demo && !props.vozDemo()) throw new ConflictException("El modo demo del canal de voz está apagado (VOZ_DEMO).");
    ZonedDateTime ahora = ahora();
    if (!demo && !ventanaAbierta(ahora)) throw new ConflictException(copy.texto("voz.fuera_de_ventana"));
    Cliente c = contexto.cliente(clienteId);
    Evaluacion ev = demo ? evaluarDemo(c) : evaluar(c, ahora.toLocalDate());
    if (!ev.llamable()) throw new ConflictException(copy.render("voz.no_llamable", Map.of("motivo", ev.motivo())));

    return registrar(c, ev, c.getTelefono(), copy.texto("voz.proveedor"));
  }

  /**
   * Demo sin número: la llamada es web (navegador y micrófono) y usa el mismo asistente,
   * las mismas herramientas en n8n y las mismas reglas. Solo con VOZ_DEMO.
   */
  @Transactional
  public Voz.Programada programarWeb(String clienteId) {
    if (!props.vozDemo()) throw new ConflictException("El modo demo del canal de voz está apagado (VOZ_DEMO).");
    Cliente c = contexto.cliente(clienteId);
    Contexto.Datos datos = contexto.cargar(c.getId());
    Credito credito = datos.principalConCuota()
        .orElseThrow(() -> new ConflictException(copy.render("voz.no_llamable", Map.of("motivo", "no tiene crédito con cuota"))));
    return registrar(c, new Evaluacion(null, datos, credito, 0, 0), "web", copy.texto("voz.proveedor") + "-web");
  }

  private Voz.Programada registrar(Cliente c, Evaluacion ev, String telefono, String proveedor) {
    LlamadaVoz l = new LlamadaVoz();
    l.setId("llam-" + UUID.randomUUID());
    l.setClienteId(c.getId());
    l.setCreditoId(ev.credito().getId());
    l.setTelefono(telefono);
    l.setProveedor(proveedor);
    l.setIntento(ev.intentos() + 1);
    l.setEstado(LlamadaVoz.PROGRAMADA);
    llamadas.save(l);
    boolean web = "web".equals(telefono);
    auditoria.info(AuditoriaService.VOZ, web ? "voz.llamada.web" : "voz.llamada.programada", c.getId(), l.getId(),
        (web ? "Llamada web o emulada lista" : "Llamada programada al " + enmascarar(telefono)) + " · intento " + l.getIntento(),
        Map.of("proveedor", proveedor, "credito", ev.credito().getId()));
    return new Voz.Programada(l.getId(), telefono, variables(c, ev, l));
  }

  private Map<String, Object> variables(Cliente c, Evaluacion ev, LlamadaVoz l) {
    LocalDate hoy = LocalDate.now();
    int diaActual = fechaCobro.diaActual(ev.datos(), ev.credito());
    String telebanca = copy.texto("voz.telebanca_hablado");
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("llamadaId", l.getId());
    v.put("clienteId", c.getId());
    v.put("nombre", c.getFirstName());
    v.put("saludo", copy.texto(ahora().getHour() < 12 ? "voz.saludo_manana" : "voz.saludo_tarde"));
    v.put("banco", copy.texto("voz.banco"));
    v.put("asistente", copy.texto("voz.asistente"));
    v.put("cuota", Fechas.montoHablado(ev.credito().getInstallmentAmount()));
    v.put("diaCobro", String.valueOf(diaActual));
    v.put("proximoCobro", Fechas.etiqueta(Fechas.desdeDia(hoy, diaActual)));
    v.put("usaApp", usaApp(c) ? "si" : "no");
    v.put("agencia", agenciaCercana(c)
        .map(s -> copy.render("voz.agencia", Map.of("nombre", s.getNombre(), "direccion", s.getDireccion(), "horario", s.getHorario())))
        .orElseGet(() -> copy.render("voz.sin_agencia", Map.of("telebanca", telebanca))));
    v.put("telebanca", telebanca);
    v.put("mesesBloqueo", copy.texto("fecha.meses_bloqueo"));
    return v;
  }

  private boolean usaApp(Cliente c) {
    return !dispositivos.findByClienteIdAndActivoTrue(c.getId()).isEmpty();
  }

  private Optional<Sucursal> agenciaCercana(Cliente c) {
    List<Sucursal> activas = sucursales.findByActivoTrueOrderByNombreAsc();
    return activas.stream().filter(s -> igual(s.getMunicipio(), c.getMunicipio())).findFirst()
        .or(() -> activas.stream().filter(s -> igual(s.getDepartamento(), c.getDepartamento())).findFirst());
  }

  private static boolean igual(String a, String b) {
    return a != null && b != null && Guardrails.normalizar(a).equals(Guardrails.normalizar(b));
  }

  // --- Herramientas del agente ---------------------------------------------------

  @Transactional(readOnly = true)
  public Voz.Propuesta propuesta(Voz.PedirPropuesta req) {
    LlamadaVoz llamada = llamadaEnCurso(req.llamadaId(), req.clienteId());
    Contexto.Datos datos = contexto.cargar(llamada.getClienteId());
    Credito credito = datos.principalConCuota()
        .orElseThrow(() -> new ConflictException("La persona no tiene un crédito con cuota para mover."));
    String telebanca = copy.texto("voz.telebanca_hablado");
    Optional<LocalDate> bloqueo = fechaCobro.bloqueadoHasta(datos, credito);
    if (bloqueo.isPresent()) {
      auditoria.info(AuditoriaService.VOZ, "voz.propuesta", llamada.getClienteId(), llamada.getId(),
          "No se puede mover la fecha: bloqueada hasta el " + Fechas.etiquetaAnio(bloqueo.get()), null);
      return new Voz.Propuesta(true, copy.render("voz.bloqueado",
          Map.of("hasta", Fechas.etiquetaAnio(bloqueo.get()), "telebanca", telebanca)), List.of());
    }
    Ingreso ingreso = ingreso(req.tipoIngreso(), req.diasIngreso(), req.semanal(), req.constante());
    FechaCobroService.Opciones opciones = fechaCobro.calcularPorDias(datos, ingreso.frecuencia(), ingreso.dias(), ingreso.etiqueta());
    // Semanal y variable sin días salen del catálogo y del ledger: ahí no se puede prometer «N días después de tu pago».
    String plantilla = opciones.frecuencia().isSemanal() ? "voz.opcion_semanal"
        : opciones.frecuencia().isVariable() && ingreso.dias().isEmpty() ? "voz.opcion_variable" : "voz.opcion";
    List<Voz.OpcionVoz> ofrecidas = opciones.todas().stream()
        .limit(copy.entero("voz.max_opciones"))
        .map(o -> opcionVoz(credito, opciones, o, plantilla))
        .toList();
    if (ofrecidas.isEmpty()) {
      auditoria.info(AuditoriaService.VOZ, "voz.propuesta", llamada.getClienteId(), llamada.getId(),
          "Sin fechas posibles para los días " + ingreso.dias(), null);
      return new Voz.Propuesta(false, copy.render("voz.sin_opciones", Map.of("telebanca", telebanca)), List.of());
    }
    String decir = ofrecidas.size() == 1
        ? copy.render("voz.propuesta_una", Map.of("opcion", ofrecidas.get(0).decir()))
        : copy.render("voz.propuesta_dos", Map.of("a", ofrecidas.get(0).decir(), "b", ofrecidas.get(1).decir()));
    auditoria.info(AuditoriaService.VOZ, "voz.propuesta", llamada.getClienteId(), llamada.getId(),
        "Propuso " + ofrecidas.stream()
            .map(o -> "día " + o.dia() + (o.interes() > 0 ? String.format(Locale.ROOT, " ($%.2f de interés)", o.interes()) : " (sin interés)"))
            .collect(Collectors.joining(" o ")) + " · ingreso: " + ingreso.tipo() + (ingreso.dias().isEmpty() ? "" : " " + ingreso.dias()),
        Map.of("frecuencia", ingreso.frecuencia(), "opciones", ofrecidas));
    return new Voz.Propuesta(false, decir, ofrecidas);
  }

  private Voz.OpcionVoz opcionVoz(Credito credito, FechaCobroService.Opciones opciones, FechaCobroService.Opcion o, String plantilla) {
    FechaCobroService.Costo costo = fechaCobro.costo(credito, opciones.hoy(), o.desde());
    StringBuilder decir = new StringBuilder(copy.render(plantilla,
        Map.of("dia", o.dia(), "margen", o.margen(), "pago", o.pago(), "desde", Fechas.etiqueta(o.desde()))));
    decir.append(' ');
    if (costo.interes() == null) {
      decir.append(copy.render("voz.opcion_interes_sin_datos", Map.of("dias", costo.diasExtra())));
    } else if (costo.diasExtra() == 0 || costo.interes().signum() == 0) {
      decir.append(copy.texto("voz.opcion_sin_interes"));
    } else {
      decir.append(copy.render("voz.opcion_interes",
          Map.of("dias", costo.diasExtra(), "monto", Fechas.montoHablado(costo.interes()))));
    }
    double interes = costo.interes() == null ? 0 : Fechas.d(costo.interes());
    return new Voz.OpcionVoz(o.dia(), Fechas.etiquetaAnio(o.desde()), costo.diasExtra(), interes, decir.toString());
  }

  @Transactional
  public Voz.Confirmado confirmar(Voz.ConfirmarFecha req) {
    LlamadaVoz llamada = llamadaEnCurso(req.llamadaId(), req.clienteId());
    Contexto.Datos datos = contexto.cargar(llamada.getClienteId());
    Ingreso ingreso = ingreso(req.tipoIngreso(), req.diasIngreso(), req.semanal(), req.constante());
    FechaCobroService.Opciones opciones = fechaCobro.calcularPorDias(datos, ingreso.frecuencia(), ingreso.dias(), ingreso.etiqueta());
    FechaCobroService.Confirmacion conf = fechaCobro.confirmar(datos, opciones, req.dia(), null,
        PlanFechaCobro.CANAL_VOZ, Boolean.TRUE.equals(req.aceptaInteres()));
    guardarPerfil(llamada.getClienteId(), ingreso, null, null);

    PlanFechaCobro plan = conf.plan();
    llamada.setCreditoId(conf.credito().getId());
    llamada.setDiaNuevo(plan.getNewDay());
    llamada.setDiasExtra(plan.getDiasExtra());
    llamada.setInteresExtra(plan.getInteresExtra());
    llamada.setResultado(LlamadaVoz.FECHA_CAMBIADA);
    llamadas.save(llamada);

    String telebanca = copy.texto("voz.telebanca_hablado");
    StringBuilder decir = new StringBuilder(copy.render("voz.confirmado",
        Map.of("dia", plan.getNewDay(), "desde", Fechas.etiqueta(conf.opcion().desde()))));
    if (plan.isAceptoInteres()) {
      decir.append(' ').append(copy.render("voz.confirmado_interes", Map.of("monto", Fechas.montoHablado(plan.getInteresExtra()))));
    }
    decir.append(' ').append(copy.render("voz.confirmado_bloqueo",
        Map.of("meses", copy.texto("fecha.meses_bloqueo"), "telebanca", telebanca)));
    return new Voz.Confirmado(plan.getNewDay(), Fechas.etiquetaAnio(conf.opcion().desde()), plan.getDiasExtra(),
        Fechas.d(plan.getInteresExtra()), Fechas.etiquetaAnio(plan.getBloqueadoHasta()), decir.toString());
  }

  // --- Fin de la llamada ---------------------------------------------------------

  @Transactional
  public void resultado(Voz.Resultado req) {
    if (req.llamadaId() == null || req.llamadaId().isBlank()) throw new IllegalArgumentException("Falta el id de la llamada.");
    LlamadaVoz l = llamadas.findById(req.llamadaId()).orElseThrow(() -> new NotFoundException("No encontramos esa llamada."));
    if (req.clienteId() != null && !req.clienteId().isBlank() && !req.clienteId().equals(l.getClienteId())) {
      throw new UnauthorizedException("La llamada no corresponde a ese cliente.");
    }
    // Llega dos veces: el formulario que guarda el agente antes de despedirse (sin motivo de fin) y
    // el reporte del proveedor al colgar. Ninguna de las dos borra lo que trajo la otra.
    String motivo = recortar(req.motivoFin(), 64);
    if (motivo != null) {
      l.setMotivoFin(motivo);
      l.setEstado(motivo.contains("error") || motivo.contains("failed") ? LlamadaVoz.FALLIDA : LlamadaVoz.TERMINADA);
      l.setEndedAt(LocalDateTime.now());
    }
    boolean sinContacto = motivo != null && resultadoDe(motivo, null) != LlamadaVoz.SIN_CAMBIO;
    if (!LlamadaVoz.FECHA_CAMBIADA.equals(l.getResultado())
        && (l.getResultado() == null || req.resultado() != null || sinContacto)) {
      l.setResultado(resultadoDe(motivo, req.resultado()));
    }
    if (req.proveedorId() != null) l.setProveedorId(recortar(req.proveedorId(), 64));
    if (req.resumen() != null) l.setResumen(recortar(req.resumen(), 1000));
    if (req.transcripcion() != null) l.setTranscripcion(req.transcripcion());
    if (req.duracionSeg() != null) l.setDuracionSeg(req.duracionSeg());
    llamadas.save(l);
    if (motivo == null) {
      auditoria.info(AuditoriaService.VOZ, "voz.formulario", l.getClienteId(), l.getId(),
          "Formulario guardado: " + (req.tipoIngreso() == null ? "sin tipo de ingreso" : req.tipoIngreso())
              + (req.diasIngreso() == null || req.diasIngreso().isEmpty() ? "" : " · días " + req.diasIngreso())
              + (req.canalPago() == null ? "" : " · paga por " + req.canalPago()) + " · resultado " + l.getResultado(), null);
    } else {
      auditoria.registrar(AuditoriaService.VOZ, "voz.llamada.terminada",
          LlamadaVoz.FALLIDA.equals(l.getEstado()) ? AuditoriaService.AVISO : AuditoriaService.INFO, l.getClienteId(), l.getId(),
          "Llamada terminada: " + l.getResultado() + " · " + motivo + (l.getDuracionSeg() == null ? "" : " · " + l.getDuracionSeg() + " s"),
          null, null);
    }

    boolean habloDeIngreso = req.tipoIngreso() != null && !req.tipoIngreso().isBlank();
    if (habloDeIngreso || req.canalPago() != null || req.usaBancaLinea() != null) {
      Ingreso ingreso = habloDeIngreso ? ingreso(req.tipoIngreso(), req.diasIngreso(), null, req.constante()) : null;
      guardarPerfil(l.getClienteId(), ingreso, req.canalPago(), req.usaBancaLinea());
      Cliente c = contexto.cliente(l.getClienteId());
      if (ingreso != null && c.getFrecuenciaPago() == null) {
        c.setFrecuenciaPago(calendario.porSlugOId(ingreso.frecuencia()).getId());
        clientes.save(c);
      }
    }
  }

  private static String resultadoDe(String motivo, String delModelo) {
    String m = motivo == null ? "" : motivo;
    if (m.contains("voicemail")) return LlamadaVoz.BUZON;
    if (m.contains("did-not-answer") || m.contains("busy") || m.contains("failed")) return LlamadaVoz.NO_CONTESTO;
    String r = Guardrails.normalizar(delModelo);
    return RESULTADOS_DEL_MODELO.contains(r) ? r : LlamadaVoz.SIN_CAMBIO;
  }

  // --- Apoyo ---------------------------------------------------------------------

  /** La llamada existe, es de ese cliente y sigue en curso: evita que un id viejo o ajeno mueva una fecha. */
  private LlamadaVoz llamadaEnCurso(String llamadaId, String clienteId) {
    if (llamadaId == null || llamadaId.isBlank()) throw new UnauthorizedException("La llamada no trae su identificador.");
    LlamadaVoz l = llamadas.findById(llamadaId).orElseThrow(() -> new NotFoundException("No encontramos esa llamada."));
    if (clienteId != null && !clienteId.isBlank() && !clienteId.equals(l.getClienteId())) {
      throw new UnauthorizedException("La llamada no corresponde a ese cliente.");
    }
    if (!LlamadaVoz.PROGRAMADA.equals(l.getEstado())) throw new ConflictException("Esa llamada ya terminó.");
    return l;
  }

  /**
   * Traduce lo que contó la persona a la frecuencia del catálogo: semanal si cobra
   * cada semana; variable si no es constante o no dio días; dos días o más, quincena
   * y fin de mes; uno, una vez al mes.
   */
  private Ingreso ingreso(String tipo, List<Integer> dias, Boolean semanal, Boolean constante) {
    String t = Guardrails.normalizar(tipo);
    if (!TIPOS_INGRESO.contains(t)) t = "otro";
    List<Integer> ds = dias == null ? List.of()
        : dias.stream().filter(d -> d != null && d >= 1 && d <= 31).distinct().sorted().toList();
    String frecuencia = Boolean.TRUE.equals(semanal) ? "semanal"
        : Boolean.FALSE.equals(constante) || ds.isEmpty() ? "variable"
        : ds.size() >= 2 ? "quincena-fin-de-mes" : "fin-de-mes";
    return new Ingreso(t, frecuencia, ds, copy.texto("voz.ingreso." + t), constante);
  }

  private void guardarPerfil(String clienteId, Ingreso ingreso, String canalPago, Boolean usaBancaLinea) {
    PerfilIngreso p = perfiles.findById(clienteId).orElseGet(() -> {
      PerfilIngreso nuevo = new PerfilIngreso();
      nuevo.setClienteId(clienteId);
      nuevo.setFuente(PlanFechaCobro.CANAL_VOZ);
      return nuevo;
    });
    if (ingreso != null) {
      p.setTipoIngreso(ingreso.tipo());
      if (ingreso.constante() != null) p.setIngresoConstante(ingreso.constante());
      if (!ingreso.dias().isEmpty()) p.setDiasIngreso(ingreso.dias().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }
    if (p.getTipoIngreso() == null) p.setTipoIngreso("otro");
    String canal = Guardrails.normalizar(canalPago);
    if (CANALES_PAGO.contains(canal)) p.setCanalPago(canal);
    if (usaBancaLinea != null) p.setUsaBancaLinea(usaBancaLinea);
    perfiles.save(p);
  }

  /** «+503…00»: en el registro no hace falta el número completo. */
  private static String enmascarar(String telefono) {
    if (telefono == null || telefono.length() < 7) return telefono;
    return telefono.substring(0, 4) + "…" + telefono.substring(telefono.length() - 2);
  }

  private static String recortar(String texto, int max) {
    if (texto == null || texto.isBlank()) return null;
    return texto.length() > max ? texto.substring(0, max) : texto;
  }
}
