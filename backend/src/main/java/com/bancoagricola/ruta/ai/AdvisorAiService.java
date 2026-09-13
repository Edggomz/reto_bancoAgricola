package com.bancoagricola.ruta.ai;

import com.bancoagricola.ruta.domain.Aviso;
import com.bancoagricola.ruta.domain.CatalogoChatOpcion;
import com.bancoagricola.ruta.domain.CatalogoFrecuencia;
import com.bancoagricola.ruta.domain.ChatMensaje;
import com.bancoagricola.ruta.domain.ChatQuickReply;
import com.bancoagricola.ruta.domain.ChatSesion;
import com.bancoagricola.ruta.domain.Cita;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.ShockContext;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.error.ConflictException;
import com.bancoagricola.ruta.error.NotFoundException;
import com.bancoagricola.ruta.repository.Repositorios;
import com.bancoagricola.ruta.service.AuditoriaService;
import com.bancoagricola.ruta.service.ApartadoService;
import com.bancoagricola.ruta.service.AvisoService;
import com.bancoagricola.ruta.service.CalendarioPagos;
import com.bancoagricola.ruta.service.CitaService;
import com.bancoagricola.ruta.service.Contexto;
import com.bancoagricola.ruta.service.CopyService;
import com.bancoagricola.ruta.service.FechaCobroService;
import com.bancoagricola.ruta.service.Fechas;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El asesor bancario con IA (13 · 14 · 14b · 15). Skill: RESOLVER PRIMERO con
 * acciones que se ejecutan en el mismo turno (mover la fecha, apartar en
 * partes, dejarlo en automático) y ESCALAR a una persona solo si el caso se
 * sale de las manos (lo pide, no puede pagar, rechaza todo, o la conversación
 * se alarga). Si dice que no, no insiste.
 *
 * El modelo (Gemini Flash, fallback Groq) clasifica la intención y redacta las
 * respuestas libres; las acciones, montos, fechas y cierres los decide el
 * código con los datos de la base. Todo queda registrado: transcripción,
 * resultado (acuerdo | negativa | siguiente_paso | escalado) y fecha acordada.
 * Sin llaves de IA el guion determinista cubre el flujo completo.
 */
@Service
public class AdvisorAiService {
  private static final Logger log = LoggerFactory.getLogger(AdvisorAiService.class);
  private static final Set<String> INTENCIONES = Set.of("fecha", "apartar", "automatico", "asesora", "no_puede", "rechazo",
      "pensar", "gracias", "saludo", "pregunta", "fuera_de_tema", "jailbreak", "dia", "partes", "frecuencia", "si", "no");
  private static final Set<String> FRECUENCIAS = Set.of("quincena-fin-de-mes", "fin-de-mes", "semanal", "variable");
  private static final Pattern SI = Pattern.compile("^(si|sí|claro|dale|va|ok|okay|por favor|si, por favor|si por favor|de acuerdo|perfecto|listo|esta bien|agenda)\\b");
  private static final Pattern NO = Pattern.compile("^(no|nel|no gracias|no, gracias|ahora no|mejor no|no quiero)\\b");
  private static final Pattern DIA = Pattern.compile("^(?:el\\s+)?(?:dia\\s+)?(\\d{1,2})$");
  private static final Pattern PARTES = Pattern.compile("^(?:en\\s+)?(\\d)\\s*partes?$");
  private static final Pattern NO_PUEDE = Pattern.compile("no puedo pagar|no tengo (dinero|como|plata|con que|trabajo)|sin trabajo|me despidieron|me quede sin|no me alcanza para nada|no voy a poder|no puedo pagar nada");
  private static final Pattern ASESORA = Pattern.compile("\\b(persona|humano|humana|asesor|asesora|agente|ejecutiv[oa]|cita|llamada|llamar|hablar con alguien|atencion al cliente)\\b");
  private static final Pattern AUTOMATICO = Pattern.compile("automatic|se pague sol|debito automatico|cargo automatico|que se pague|se cobre sol");
  private static final Pattern FECHA = Pattern.compile("\\b(fecha|mover|cambiar el dia|cambiar mi dia|cambiar la fecha|me pagan|cobro|cobran|dia de pago|dia de cobro|quincena|fin de mes)\\b");
  private static final Pattern APARTAR = Pattern.compile("\\b(apartar|aparta|apartamos|partes|repartir|dividir|fraccionar|en dos|en tres|en cuatro|congelar)\\b");
  private static final Pattern RECHAZO = Pattern.compile("^(no me interesa|ninguna|no me sirve|no quiero|no me convence|nada de eso|ninguna opcion)");
  /** Paso del guion mientras la persona decide si acepta el interés de ese día: «interes:3». */
  private static final String PASO_INTERES = "interes:";
  private static final Pattern PENSAR = Pattern.compile("lo pienso|despues|luego|mas tarde|otro dia|lo veo|ahorita no|ahora no|lo pienso y lo veo despues");
  private static final Pattern GRACIAS = Pattern.compile("^(gracias|listo|perfecto|eso es todo|ya esta|muchas gracias|listo, gracias|listo gracias|ok gracias)\\b");
  private static final Pattern SALUDO = Pattern.compile("^(hola|buenos dias|buenas|buen dia|hey|que tal|buenas tardes|buenas noches)\\b");

  private final Repositorios.Sesiones sesiones;
  private final Repositorios.Mensajes mensajes;
  private final Repositorios.QuickReplies quickReplies;
  private final Repositorios.OpcionesChat opcionesChat;
  private final Repositorios.Avisos avisosRepo;
  private final Repositorios.Choques choques;
  private final Contexto contexto;
  private final CopyService copy;
  private final LlmRouter router;
  private final Guardrails guardrails;
  private final FechaCobroService fechaCobro;
  private final ApartadoService apartado;
  private final CitaService citas;
  private final CalendarioPagos calendario;
  private final AuditoriaService auditoria;

  public AdvisorAiService(Repositorios.Sesiones sesiones, Repositorios.Mensajes mensajes, Repositorios.QuickReplies quickReplies,
                          Repositorios.OpcionesChat opcionesChat, Repositorios.Avisos avisosRepo, Repositorios.Choques choques,
                          Contexto contexto, CopyService copy, LlmRouter router, Guardrails guardrails,
                          FechaCobroService fechaCobro, ApartadoService apartado, CitaService citas, CalendarioPagos calendario, AuditoriaService auditoria) {
    this.sesiones = sesiones;
    this.mensajes = mensajes;
    this.quickReplies = quickReplies;
    this.opcionesChat = opcionesChat;
    this.avisosRepo = avisosRepo;
    this.choques = choques;
    this.contexto = contexto;
    this.copy = copy;
    this.router = router;
    this.guardrails = guardrails;
    this.fechaCobro = fechaCobro;
    this.apartado = apartado;
    this.citas = citas;
    this.calendario = calendario;
    this.auditoria = auditoria;
  }

  /** Intención detectada en un turno (etiqueta, heurística o modelo) y datos que trae. */
  record Intencion(String tipo, Integer dia, Integer partes, String frecuencia, String respuesta, String origen, String proveedor) {
    static Intencion de(String tipo) {
      return new Intencion(tipo, null, null, null, null, "guion", null);
    }
  }

  /** Lo que el asesor contesta: uno o más mensajes y las opciones que puede tocar la persona. */
  record Turno(List<String> textos, List<String> sugerencias, String origen, String proveedor) {
    static Turno guion(String texto, List<String> sugerencias) {
      return new Turno(List.of(texto), sugerencias, "guion", null);
    }
  }

  // ---------------------------------------------------------------------------
  // Apertura
  // ---------------------------------------------------------------------------
  @Transactional
  public App.SesionAsesor iniciar(String clienteId, String avisoId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Map<String, Object> vars = variables(datos);
    ChatSesion s = new ChatSesion();
    s.setId("chat-" + UUID.randomUUID());
    s.setClienteId(clienteId);
    s.setAvisoId(avisoId);
    s.setResultado(ChatSesion.EN_CURSO);
    s.setTurnos(0);
    s.setRechazos(0);
    sesiones.save(s);
    auditoria.info(AuditoriaService.CHAT, "chat.iniciado", clienteId, s.getId(), avisoId == null ? "Abrió el asesor" : "Abrió el asesor desde un aviso", null);

    Optional<Aviso> aviso = avisoId == null ? Optional.empty()
        : avisosRepo.findById(avisoId).filter(a -> a.getClienteId().equals(clienteId));
    Optional<ShockContext> choque = choques.findById(clienteId);
    String texto;
    if (aviso.isPresent() && ("shock".equals(aviso.get().getKind()) || "advisory-shock".equals(aviso.get().getTarget())) && choque.isPresent()) {
      String clave = "chat.apertura.shock." + datos.cliente().getArchetype();
      texto = copy.render(existe(clave) ? clave : "chat.apertura.shock.diligente", vars);
    } else {
      texto = copy.render("chat.apertura.general", vars);
    }
    aviso.ifPresent(a -> {
      a.setReadFlag(true);
      avisosRepo.save(a);
    });
    List<String> sugerencias = ofertas(datos);
    App.MensajeAsesor m = guardarAsistente(s, texto, sugerencias, "guion", null, 1);
    return new App.SesionAsesor(s.getId(), List.of(m), sugerencias);
  }

  // ---------------------------------------------------------------------------
  // Turno
  // ---------------------------------------------------------------------------
  @Transactional
  public App.RespuestaAsesor enviar(String clienteId, String sesionId, String texto) {
    ChatSesion s = sesiones.findById(sesionId).filter(x -> x.getClienteId().equals(clienteId))
        .orElseThrow(() -> new NotFoundException("No encontramos esa conversación."));
    String limpio = texto == null ? "" : texto.trim();
    if (limpio.isEmpty()) throw new IllegalArgumentException("Escribe tu mensaje.");
    if (limpio.length() > 500) limpio = limpio.substring(0, 500);
    if (s.cerrada()) {
      s.setClosedAt(null);
      s.setResultado(ChatSesion.EN_CURSO);
      s.setPaso(null);
    }
    int orden = (int) mensajes.countBySesionId(s.getId());
    guardarUsuario(s, limpio, ++orden);
    s.setTurnos(s.getTurnos() + 1);

    Contexto.Datos datos = contexto.cargar(clienteId);
    Map<String, Object> vars = variables(datos);
    Intencion in = clasificar(limpio, s, datos, vars);
    Turno t = responder(in, s, datos, vars);

    if (!s.cerrada() && ChatSesion.EN_CURSO.equals(s.getResultado()) && !"escalar".equals(s.getPaso())
        && s.getTurnos() >= copy.entero("chat.max_turnos")) {
      Turno esc = escalar(s, datos, vars, copy.render("chat.escalar.turnos", vars) + " ");
      t = new Turno(esc.textos(), esc.sugerencias(), esc.origen(), esc.proveedor());
    }
    sesiones.save(s);

    List<App.MensajeAsesor> salida = new ArrayList<>();
    for (int i = 0; i < t.textos().size(); i++) {
      boolean ultimo = i == t.textos().size() - 1;
      salida.add(guardarAsistente(s, t.textos().get(i), ultimo ? t.sugerencias() : List.of(), t.origen(), t.proveedor(), ++orden));
    }
    return new App.RespuestaAsesor(salida, t.sugerencias());
  }

  @Transactional
  public void terminar(String clienteId, String sesionId) {
    sesiones.findById(sesionId).filter(x -> x.getClienteId().equals(clienteId)).ifPresent(s -> {
      if (ChatSesion.EN_CURSO.equals(s.getResultado())) s.setResultado(ChatSesion.SIGUIENTE_PASO);
      s.setClosedAt(LocalDateTime.now());
      s.setPaso(null);
      sesiones.save(s);
    });
  }

  // ---------------------------------------------------------------------------
  // Clasificación: etiquetas -> guard -> heurística -> modelo
  // ---------------------------------------------------------------------------
  private Intencion clasificar(String texto, ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    String n = Guardrails.normalizar(texto);
    String paso = s.getPaso();
    // Preguntas de sí o no: escalar a una persona o aceptar el interés del día elegido.
    if ("escalar".equals(paso) || (paso != null && paso.startsWith(PASO_INTERES))) {
      if (SI.matcher(n).find()) return Intencion.de("si");
      if (NO.matcher(n).find() || n.startsWith("elegir otro")) return Intencion.de("no");
    }
    for (CatalogoChatOpcion o : opcionesChat.findByActivoTrueOrderByOrdenAsc()) {
      if (Guardrails.normalizar(o.getLabel()).equals(n)) return Intencion.de(porAccion(o.getAccion()));
    }
    if (n.equals("listo, gracias") || n.equals("listo gracias")) return Intencion.de("gracias");
    for (CatalogoFrecuencia f : calendario.todas()) {
      if (Guardrails.normalizar(f.getLabel()).equals(n) || f.getSlug().equals(n)) {
        return new Intencion("frecuencia", null, null, f.getSlug(), null, "guion", null);
      }
    }
    Matcher md = DIA.matcher(n);
    if (md.matches() && ("dia".equals(paso) || n.startsWith("dia") || n.startsWith("el "))) {
      return new Intencion("dia", Integer.parseInt(md.group(1)), null, null, null, "guion", null);
    }
    Matcher mp = PARTES.matcher(n);
    if (mp.matches()) return new Intencion("partes", null, Integer.parseInt(mp.group(1)), null, null, "guion", null);
    if ("partes".equals(paso) && n.matches("^\\d$")) return new Intencion("partes", null, Integer.parseInt(n), null, null, "guion", null);

    if (guardrails.esInyeccion(texto, s.getId())) return Intencion.de("jailbreak");

    String h = heuristica(n, paso);
    // Temas ajenos detectados por regla: respuesta determinista, sin gastar una llamada al modelo.
    if (router.disponible() && !"fuera_de_tema".equals(h)) {
      Optional<Intencion> modelo = clasificarConModelo(texto, s, datos, vars, h);
      if (modelo.isPresent()) return modelo.get();
    }
    return Intencion.de(h);
  }

  private static String porAccion(String accion) {
    return switch (accion == null ? "" : accion) {
      case "fecha" -> "fecha";
      case "apartar" -> "apartar";
      case "automatico" -> "automatico";
      case "asesora" -> "asesora";
      case "seguir" -> "pensar";
      case "agendar" -> "si";
      case "no_gracias" -> "no";
      default -> "pregunta";
    };
  }

  private String heuristica(String n, String paso) {
    if (NO_PUEDE.matcher(n).find()) return "no_puede";
    if (ASESORA.matcher(n).find()) return "asesora";
    if (AUTOMATICO.matcher(n).find()) return "automatico";
    if (APARTAR.matcher(n).find()) return "apartar";
    if (FECHA.matcher(n).find()) return "fecha";
    if (RECHAZO.matcher(n).find() || (NO.matcher(n).find() && paso == null)) return "rechazo";
    if (PENSAR.matcher(n).find()) return "pensar";
    if (GRACIAS.matcher(n).find()) return "gracias";
    if (SALUDO.matcher(n).find()) return "saludo";
    if (SI.matcher(n).find() && paso == null) return "pregunta";
    if (guardrails.esFueraDeTema(n)) return "fuera_de_tema";
    return "pregunta";
  }

  private Optional<Intencion> clasificarConModelo(String texto, ChatSesion s, Contexto.Datos datos, Map<String, Object> vars, String heuristica) {
    try {
      // Solo las preguntas libres usan el modelo principal (redacta con más criterio); el resto, el rápido.
      boolean libre = "pregunta".equals(heuristica);
      String user = contextoModelo(s, datos, vars) + "\nMENSAJE DEL CLIENTE: \"" + texto.replace("\"", "'") + "\"\n"
          + "Pista de la heurística del sistema: " + heuristica + ". Clasifica y responde solo con el JSON.";
      Optional<LlmRouter.Respuesta> r = router.generar(new LlmRouter.Solicitud("chat", SystemPrompts.ADVISOR, user, true,
          !libre, 500, 0.4, s.getId()));
      if (r.isEmpty()) return Optional.empty();
      Optional<JsonNode> json = router.json(r.get().texto());
      if (json.isEmpty()) return Optional.empty();
      JsonNode j = json.get();
      String tipo = j.path("intencion").asText("");
      if (!INTENCIONES.contains(tipo)) tipo = heuristica;
      Integer dia = j.path("dia").isNumber() ? j.path("dia").asInt() : null;
      Integer partes = j.path("partes").isNumber() ? j.path("partes").asInt() : null;
      String frecuencia = FRECUENCIAS.contains(j.path("frecuencia").asText("")) ? j.path("frecuencia").asText() : null;
      String respuesta = guardrails.sanitize(j.path("respuesta").asText(""));
      return Optional.of(new Intencion(tipo, dia, partes, frecuencia, respuesta, "modelo", r.get().proveedor()));
    } catch (Exception e) {
      log.warn("Clasificación con modelo falló: {}", e.toString());
      return Optional.empty();
    }
  }

  private String contextoModelo(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    StringBuilder b = new StringBuilder("CONTEXTO\n");
    String arq = datos.cliente().getArchetype();
    b.append("- Cliente: ").append(datos.cliente().getFirstName()).append(", arquetipo ").append(arq)
        .append(" (").append(existe("chat.tono." + arq) ? copy.texto("chat.tono." + arq) : "").append("). Tarjeta ")
        .append(datos.cliente().getCardTier()).append(".\n");
    b.append("- Asesora: ").append(vars.get("asesora")).append(", agencia ").append(vars.get("agencia")).append(".\n");
    b.append("- Productos:");
    for (Credito c : datos.creditos()) {
      b.append(" ").append(c.getName()).append(" ").append(c.getNumberMasked());
      if (c.esTarjeta()) {
        b.append(" (pago de contado ").append(Fechas.monto(c.getPayContado())).append(", corte día ").append(c.getDiaCorte()).append(")");
      } else {
        int dia = apartado.diaCobro(datos, c);
        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDate desde = datos.plan(c).map(p -> p.getEffectiveFrom()).filter(d -> d != null && d.isAfter(hoy)).orElse(hoy.plusDays(1));
        b.append(" (cuota ").append(Fechas.monto(c.getInstallmentAmount())).append(", cobro día ").append(dia)
            .append(", próximo cobro el ").append(Fechas.etiqueta(Fechas.desdeDia(desde, dia))).append(")");
      }
      b.append(";");
    }
    b.append("\n- Cuenta en este banco: ").append(datos.origen().map(c -> c.getNumberMasked() + ", saldo disponible " + Fechas.monto(c.getBalanceAvailable()))
        .orElse("no tiene (para apartar hay que abrir una, sin costo)")).append(".\n");
    b.append("- Le pagan: ").append(calendario.delCliente(datos).map(CatalogoFrecuencia::getLabel).orElse("aún no lo ha dicho")).append(".\n");
    b.append("- Ruta: ").append(datos.apartadoActivo().map(a -> "activa, " + a.getParts() + " partes, se paga el " + Fechas.etiqueta(a.getFechaPago()))
        .orElseGet(() -> datos.plan().map(p -> "fecha de cobro cambiada al día " + p.getNewDay()).orElse("sin ruta todavía"))).append(".\n");
    b.append("- Evento reciente: ").append(vars.get("evento") == null || vars.get("evento").toString().isBlank() ? "ninguno"
        : vars.get("evento") + " (monto " + vars.get("monto_choque") + ")").append(".\n");
    b.append("- Pregunta pendiente: ").append(s.getPaso() == null ? "ninguna" : s.getPaso()).append(".\n");
    b.append("HISTORIAL RECIENTE\n");
    List<ChatMensaje> hist = mensajes.findBySesionIdOrderByOrdenAsc(s.getId());
    int max = copy.entero("chat.historial_max");
    for (ChatMensaje m : hist.subList(Math.max(0, hist.size() - max), hist.size())) {
      b.append("user".equals(m.getRol()) ? "cliente: " : "asesor: ").append(m.getTexto()).append('\n');
    }
    return b.toString();
  }

  // ---------------------------------------------------------------------------
  // Respuesta según intención
  // ---------------------------------------------------------------------------
  private Turno responder(Intencion in, ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    String paso = s.getPaso();
    switch (in.tipo()) {
      case "jailbreak":
        return Turno.guion(copy.render("chat.jailbreak", vars), ofertas(datos));
      case "fuera_de_tema":
        return Turno.guion(copy.render("chat.redireccion", vars), ofertas(datos));
      case "saludo":
        if (in.respuesta() != null && !in.respuesta().isBlank()) return new Turno(List.of(in.respuesta()), ofertas(datos), "modelo", in.proveedor());
        return Turno.guion(copy.render("chat.apertura.general", vars), ofertas(datos));
      case "gracias":
        if (ChatSesion.EN_CURSO.equals(s.getResultado())) s.setResultado(ChatSesion.SIGUIENTE_PASO);
        cerrar(s);
        return Turno.guion(copy.render("chat.cerrado", vars), List.of());
      case "pensar":
        s.setResultado(ChatSesion.SIGUIENTE_PASO);
        cerrar(s);
        return Turno.guion(copy.render("chat.siguiente_paso", vars), List.of());
      case "no_puede":
      case "asesora":
        return escalar(s, datos, vars, "");
      case "si":
        if ("escalar".equals(paso)) return confirmarEscalamiento(s, datos, vars);
        if (paso != null && paso.startsWith(PASO_INTERES)) return ejecutarFecha(s, datos, vars, Integer.parseInt(paso.substring(PASO_INTERES.length())));
        return Turno.guion(copy.render("chat.ofertas", vars), ofertas(datos));
      case "no":
        if ("escalar".equals(paso)) return negativa(s, vars);
        if (paso != null && paso.startsWith(PASO_INTERES)) return preguntarDia(s, datos, vars);
        return rechazo(s, datos, vars);
      case "rechazo":
        return rechazo(s, datos, vars);
      case "frecuencia":
        if (in.frecuencia() != null) {
          fechaCobro.guardarFrecuencia(datos.cliente().getId(), in.frecuencia());
          Contexto.Datos actual = contexto.cargar(datos.cliente().getId());
          String intencion = s.getIntencion() == null ? "fecha" : s.getIntencion();
          if ("fecha".equals(intencion)) return preguntarDia(s, actual, vars);
          return preguntarPartes(s, actual, vars, "automatico".equals(intencion));
        }
        return preguntarFrecuencia(s, vars, s.getIntencion() == null ? "fecha" : s.getIntencion());
      case "dia":
        if (in.dia() != null) return ejecutarFecha(s, datos, vars, in.dia());
        return preguntarDia(s, datos, vars);
      case "partes":
        if (in.partes() != null) return ejecutarApartado(s, datos, vars, in.partes());
        return preguntarPartes(s, datos, vars, "automatico".equals(s.getIntencion()));
      case "fecha":
        if (in.dia() != null && calendario.delCliente(datos).isPresent()) {
          s.setIntencion("fecha");
          return ejecutarFecha(s, datos, vars, in.dia());
        }
        return preguntarDia(s, datos, vars);
      case "apartar":
      case "automatico":
        boolean auto = "automatico".equals(in.tipo());
        if (auto && datos.apartadoActivo().isPresent()) {
          apartado.dejarEnAutomatico(datos.cliente().getId());
          s.setResultado(ChatSesion.ACUERDO);
          s.setOfertaAceptada("qr-automatico");
          s.setFechaAcordada(datos.apartadoActivo().get().getFechaPago());
          s.setPaso(null);
          Map<String, Object> v = new HashMap<>(vars);
          v.put("fecha", Fechas.etiqueta(datos.apartadoActivo().get().getFechaPago()));
          return Turno.guion(copy.render("chat.acuerdo.automatico", v), List.of(etiqueta("qr-lo-pienso", "Listo, gracias")));
        }
        if (in.partes() != null && calendario.delCliente(datos).isPresent() && datos.origen().isPresent()) {
          s.setIntencion(auto ? "automatico" : "apartar");
          s.setCreditoId(apartado.creditoApartable(datos, s.getCreditoId()).getId());
          return ejecutarApartado(s, datos, vars, in.partes());
        }
        return preguntarPartes(s, datos, vars, auto);
      case "pregunta":
      default:
        if (in.respuesta() != null && !in.respuesta().isBlank()) {
          return new Turno(List.of(in.respuesta()), ofertas(datos), "modelo", in.proveedor());
        }
        return Turno.guion(copy.render("chat.ofertas", vars), ofertas(datos));
    }
  }

  // --- pasos del guion --------------------------------------------------------
  private Turno preguntarFrecuencia(ChatSesion s, Map<String, Object> vars, String intencion) {
    s.setPaso("frecuencia");
    s.setIntencion(intencion);
    return Turno.guion(copy.render("chat.pregunta.frecuencia", vars), calendario.todas().stream().map(CatalogoFrecuencia::getLabel).toList());
  }

  private Turno preguntarDia(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    s.setIntencion("fecha");
    Optional<CatalogoFrecuencia> f = calendario.delCliente(datos);
    if (f.isEmpty()) return preguntarFrecuencia(s, vars, "fecha");
    FechaCobroService.Opciones o;
    try {
      o = fechaCobro.calcular(datos, f.get().getSlug());
    } catch (IllegalArgumentException e) {
      s.setPaso(null);
      return Turno.guion(copy.render("chat.sin_credito", vars), List.of(etiqueta("qr-asesora", "Hablar con una persona")));
    }
    s.setPaso("dia");
    s.setCreditoId(o.credito().getId());
    List<String> chips = o.todas().stream().limit(6).map(x -> "Día " + x.dia()).toList();
    return Turno.guion(copy.render("chat.pregunta.dia", vars), chips);
  }

  private Turno preguntarPartes(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars, boolean automatico) {
    s.setIntencion(automatico ? "automatico" : "apartar");
    if (datos.apartables().isEmpty()) {
      s.setPaso(null);
      return Turno.guion(copy.render("chat.sin_credito", vars), List.of(etiqueta("qr-asesora", "Hablar con una persona")));
    }
    if (datos.origen().isEmpty()) {
      s.setPaso(null);
      s.setResultado(ChatSesion.SIGUIENTE_PASO);
      return Turno.guion(copy.render("chat.sin_cuenta", vars), List.of(etiqueta("qr-fecha", "Cambiar mi fecha de cobro"), "Listo, gracias"));
    }
    if (datos.apartadoActivo().isPresent()) {
      s.setPaso(null);
      Map<String, Object> v = new HashMap<>(vars);
      v.put("monto", Fechas.monto(datos.apartadoActivo().get().getMontoTotal()));
      v.put("partes", datos.apartadoActivo().get().getParts() + " partes");
      v.put("fecha", Fechas.etiqueta(datos.apartadoActivo().get().getFechaPago()));
      return Turno.guion(copy.render("chat.ya_tiene_ruta", v), List.of(etiqueta("qr-fecha", "Cambiar mi fecha de cobro"),
          etiqueta("qr-asesora", "Hablar con una persona"), "Listo, gracias"));
    }
    Optional<CatalogoFrecuencia> f = calendario.delCliente(datos);
    if (f.isEmpty()) return preguntarFrecuencia(s, vars, s.getIntencion());
    Credito c = apartado.creditoApartable(datos, s.getCreditoId());
    s.setCreditoId(c.getId());
    s.setPaso("partes");
    Map<String, Object> v = new HashMap<>(vars);
    v.put("nota", f.get().getNotaPartes() == null ? "" : f.get().getNotaPartes());
    return Turno.guion(copy.render("chat.pregunta.partes", v), f.get().partes().stream().map(n -> n == 1 ? "1 parte" : n + " partes").toList());
  }

  private Turno ejecutarFecha(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars, int dia) {
    Optional<CatalogoFrecuencia> f = calendario.delCliente(datos);
    if (f.isEmpty()) return preguntarFrecuencia(s, vars, "fecha");
    try {
      // Si ese día corre la cuota, primero se dice el interés; la fecha cambia con el «sí».
      boolean aceptoInteres = (PASO_INTERES + dia).equals(s.getPaso());
      if (!aceptoInteres) {
        FechaCobroService.Opciones o = fechaCobro.calcular(datos, f.get().getSlug());
        Optional<FechaCobroService.Opcion> opcion = o.buscar(dia);
        if (opcion.isPresent()) {
          FechaCobroService.Costo costo = fechaCobro.costo(o.credito(), o.hoy(), opcion.get().desde());
          if (costo.diasExtra() > 0 && (costo.interes() == null || costo.interes().signum() > 0)) {
            s.setPaso(PASO_INTERES + dia);
            Map<String, Object> v = new HashMap<>(vars);
            v.put("dia", dia);
            v.put("costo", fechaCobro.textoCosto(costo, "fecha.costo.opcion", opcion.get().desde()));
            auditoria.info(AuditoriaService.CHAT, "chat.pregunta_interes", datos.cliente().getId(), s.getId(),
                "El asesor dijo el interés antes de mover al día " + dia + ": " + v.get("costo"), null);
            return Turno.guion(copy.render("chat.pregunta.interes", v), List.of("Sí, cambiar al día " + dia, "Elegir otro día"));
          }
        }
      }
      FechaCobroService.Confirmacion conf = fechaCobro.confirmar(datos.cliente().getId(), f.get().getSlug(), dia, s.getCreditoId(), aceptoInteres);
      s.setResultado(ChatSesion.ACUERDO);
      s.setOfertaAceptada("qr-fecha");
      s.setFechaAcordada(conf.plan().getEffectiveFrom());
      s.setPaso(null);
      Map<String, Object> v = new HashMap<>(vars);
      v.put("dia", dia);
      v.put("fecha", Fechas.etiqueta(conf.plan().getEffectiveFrom()));
      v.put("costo", conf.plan().isAceptoInteres()
          ? copy.render("chat.costo.interes", Map.of("monto", Fechas.monto(conf.plan().getInteresExtra())))
          : copy.texto("chat.costo.sin_interes"));
      auditoria.info(AuditoriaService.CHAT, "chat.acuerdo", datos.cliente().getId(), s.getId(), "Acuerdo en el chat: cobro al día " + dia, null);
      List<String> chips = new ArrayList<>();
      if (datos.origen().isPresent() && datos.apartadoActivo().isEmpty()) chips.add(etiqueta("qr-apartar", "Apartar mi cuota en partes"));
      chips.add("Listo, gracias");
      return Turno.guion(copy.render("chat.acuerdo.fecha", v), chips);
    } catch (IllegalArgumentException e) {
      Turno pregunta = preguntarDia(s, datos, vars);
      return new Turno(List.of(copy.render("chat.fecha_invalida", vars)), pregunta.sugerencias(), "guion", null);
    } catch (ConflictException e) {
      s.setPaso(null);
      return Turno.guion(e.getMessage(), List.of(etiqueta("qr-asesora", "Hablar con una persona"), "Listo, gracias"));
    }
  }

  private Turno ejecutarApartado(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars, int partes) {
    if (datos.origen().isEmpty()) return preguntarPartes(s, datos, vars, "automatico".equals(s.getIntencion()));
    try {
      ApartadoService.Activacion act = apartado.activar(datos.cliente().getId(), s.getCreditoId(), partes, true);
      s.setResultado(ChatSesion.ACUERDO);
      s.setOfertaAceptada("automatico".equals(s.getIntencion()) ? "qr-automatico" : "qr-apartar");
      s.setFechaAcordada(act.plan().fechaPago());
      s.setPartesElegidas(partes);
      auditoria.info(AuditoriaService.CHAT, "chat.acuerdo", datos.cliente().getId(), s.getId(), "Acuerdo en el chat: apartar en " + partes + (partes == 1 ? " parte" : " partes"), null);
      s.setPaso(null);
      Map<String, Object> v = new HashMap<>(vars);
      v.put("monto", Fechas.monto(act.plan().total()));
      v.put("partes", partes == 1 ? "1 parte" : partes + " partes");
      v.put("fecha", Fechas.etiqueta(act.plan().fechaPago()));
      List<String> chips = new ArrayList<>();
      if (datos.plan().isEmpty() && datos.principalConCuota().isPresent()) chips.add(etiqueta("qr-fecha", "Cambiar mi fecha de cobro"));
      chips.add("Listo, gracias");
      return Turno.guion(copy.render("chat.acuerdo.apartar", v), chips);
    } catch (IllegalArgumentException e) {
      Turno pregunta = preguntarPartes(s, datos, vars, "automatico".equals(s.getIntencion()));
      return new Turno(List.of(e.getMessage()), pregunta.sugerencias(), "guion", null);
    }
  }

  private Turno rechazo(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    s.setRechazos(s.getRechazos() + 1);
    String clave = "chat.rechazos." + datos.cliente().getArchetype();
    int limite = existe(clave) ? copy.entero(clave) : 2;
    if (s.getRechazos() >= limite) return escalar(s, datos, vars, "");
    String key = s.getRechazos() == limite - 1 ? "chat.rechazo.ultimo" : "chat.rechazo.suave";
    return Turno.guion(copy.render(key, vars), ofertas(datos));
  }

  /** El canal al escalar lo decide la tarjeta (platino/black -> asesora nombrada), nunca si se ofrece o no una salida. */
  private Turno escalar(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars, String prefijo) {
    boolean asesora = copy.lista("chat.tiers_asesora").contains(datos.cliente().getCardTier());
    s.setPaso("escalar");
    String texto = prefijo + copy.render(asesora ? "chat.escalar.asesora" : "chat.escalar.centro", vars);
    List<String> chips = opcionesChat.findByActivoTrueOrderByOrdenAsc().stream()
        .filter(o -> "escalamiento".equals(o.getContexto())).map(CatalogoChatOpcion::getLabel).toList();
    return Turno.guion(texto, chips.isEmpty() ? List.of("Sí, por favor", "No, gracias") : chips);
  }

  private Turno confirmarEscalamiento(ChatSesion s, Contexto.Datos datos, Map<String, Object> vars) {
    boolean asesora = copy.lista("chat.tiers_asesora").contains(datos.cliente().getCardTier());
    String texto;
    if (asesora) {
      Cita cita = citas.agendar(datos.cliente().getId(), s.getCreditoId(), choques.existsById(datos.cliente().getId()) ? "shock" : "chat");
      Map<String, Object> v = new HashMap<>(vars);
      v.put("cuando", cita.getWhenLabel());
      v.put("donde", cita.getWhereLabel());
      v.put("tema", cita.getAboutLabel().replaceFirst("^tu ", ""));
      texto = copy.render("chat.cita.agendada", v);
    } else {
      texto = copy.render("chat.centro.confirmado", vars);
    }
    s.setResultado(ChatSesion.ESCALADO);
    auditoria.info(AuditoriaService.CHAT, "chat.escalado", datos.cliente().getId(), s.getId(), asesora ? "Escalado a su asesora, con cita" : "Escalado a Telebanca", null);
    cerrar(s);
    return Turno.guion(texto, List.of());
  }

  private Turno negativa(ChatSesion s, Map<String, Object> vars) {
    s.setResultado(ChatSesion.NEGATIVA);
    cerrar(s);
    return Turno.guion(copy.render("chat.negativa", vars), List.of());
  }

  private static void cerrar(ChatSesion s) {
    s.setClosedAt(LocalDateTime.now());
    s.setPaso(null);
  }

  // ---------------------------------------------------------------------------
  // Apoyo
  // ---------------------------------------------------------------------------
  /** Ofertas concretas en el orden del arquetipo (tono), filtradas por lo que la persona realmente tiene. */
  private List<String> ofertas(Contexto.Datos datos) {
    Map<String, CatalogoChatOpcion> porId = new HashMap<>();
    opcionesChat.findByActivoTrueOrderByOrdenAsc().forEach(o -> porId.put(o.getId(), o));
    String clave = "chat.orden." + datos.cliente().getArchetype();
    List<String> orden = existe(clave) ? copy.lista(clave) : new ArrayList<>(porId.keySet());
    List<String> out = new ArrayList<>();
    for (String id : orden) {
      CatalogoChatOpcion o = porId.get(id);
      if (o == null || !"oferta".equals(o.getContexto())) continue;
      if ("qr-automatico".equals(id) && datos.origen().isEmpty()) continue;
      if ("qr-fecha".equals(id) && datos.principalConCuota().isEmpty()) continue;
      if ("qr-apartar".equals(id) && datos.apartables().isEmpty()) continue;
      out.add(o.getLabel());
    }
    return out;
  }

  private String etiqueta(String opcionId, String porDefecto) {
    return opcionesChat.findById(opcionId).map(CatalogoChatOpcion::getLabel).orElse(porDefecto);
  }

  private boolean existe(String clave) {
    try {
      copy.texto(clave);
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }

  private Map<String, Object> variables(Contexto.Datos datos) {
    Map<String, Object> v = new HashMap<>();
    v.put("nombre", datos.cliente().getFirstName());
    v.put("asesora", datos.asesor() == null ? "tu asesora" : datos.asesor().getName());
    v.put("agencia", datos.asesor() == null ? "" : datos.asesor().getAgency());
    Optional<ShockContext> choque = choques.findById(datos.cliente().getId());
    v.put("evento", choque.map(c -> Fechas.minusculaInicial(c.getEventLabel())).orElse(""));
    v.put("tranquilidad", choque.map(ShockContext::getReassurance).orElse(copy.texto("chat.tranquilidad")));
    v.put("monto_choque", choque.map(c -> Fechas.monto(c.getAmount())).orElse(""));
    v.put("monto", datos.principalApartable().map(c -> Fechas.monto(c.montoApartable())).orElse(""));
    v.put("cuenta", datos.origen().map(c -> c.getNumberMasked()).orElse(""));
    v.put("partes", "2 partes");
    v.put("fecha", "");
    v.put("dia", "");
    return v;
  }

  private void guardarUsuario(ChatSesion s, String texto, int orden) {
    ChatMensaje m = new ChatMensaje();
    m.setId("msg-" + UUID.randomUUID());
    m.setSesionId(s.getId());
    m.setRol("user");
    m.setTexto(texto);
    m.setOrigen("usuario");
    m.setOrden(orden);
    mensajes.save(m);
  }

  private App.MensajeAsesor guardarAsistente(ChatSesion s, String texto, List<String> sugerencias, String origen, String proveedor, int orden) {
    ChatMensaje m = new ChatMensaje();
    m.setId("msg-" + UUID.randomUUID());
    m.setSesionId(s.getId());
    m.setRol("assistant");
    m.setTexto(texto);
    m.setOrigen("modelo".equals(origen) ? "modelo" : "guion");
    m.setProveedor(proveedor);
    m.setOrden(orden);
    mensajes.saveAndFlush(m);
    int i = 0;
    for (String sug : sugerencias) {
      ChatQuickReply q = new ChatQuickReply();
      q.setMensajeId(m.getId());
      q.setIdx(++i);
      q.setOpcionId(opcionesChat.findByActivoTrueOrderByOrdenAsc().stream().filter(o -> o.getLabel().equals(sug))
          .map(CatalogoChatOpcion::getId).findFirst().orElse("dyn-" + Guardrails.normalizar(sug).replaceAll("[^a-z0-9]+", "-")));
      q.setLabel(sug);
      quickReplies.save(q);
    }
    return new App.MensajeAsesor(m.getId(), "asesor", texto);
  }
}
