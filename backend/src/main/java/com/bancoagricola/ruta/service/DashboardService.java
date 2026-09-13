package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.ai.LlmRouter;
import com.bancoagricola.ruta.config.RutaProperties;
import com.bancoagricola.ruta.domain.Apartado;
import com.bancoagricola.ruta.domain.ChatSesion;
import com.bancoagricola.ruta.domain.Cita;
import com.bancoagricola.ruta.domain.Cliente;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.domain.NotificacionEnviada;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Métricas agregadas de gestión y de IA para el dashboard (GET /api/admin/metrics). */
@Service
public class DashboardService {
  private final Repositorios.Clientes clientes;
  private final Repositorios.Creditos creditos;
  private final Repositorios.Apartados apartados;
  private final Repositorios.Citas citas;
  private final Repositorios.Sesiones sesiones;
  private final Repositorios.Notificaciones notificaciones;
  private final RutaMotor motor;
  private final LlmRouter router;
  private final RutaProperties props;

  public DashboardService(Repositorios.Clientes clientes, Repositorios.Creditos creditos, Repositorios.Apartados apartados,
                          Repositorios.Citas citas, Repositorios.Sesiones sesiones, Repositorios.Notificaciones notificaciones,
                          RutaMotor motor, LlmRouter router, RutaProperties props) {
    this.clientes = clientes;
    this.creditos = creditos;
    this.apartados = apartados;
    this.citas = citas;
    this.sesiones = sesiones;
    this.notificaciones = notificaciones;
    this.motor = motor;
    this.router = router;
    this.props = props;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> metricas() {
    LocalDate hoy = LocalDate.now();
    Map<String, Object> out = new LinkedHashMap<>();
    List<Cliente> todos = clientes.findByActivoTrue();
    Map<String, Long> porPerfil = new TreeMap<>();
    Map<String, Long> porCategoria = new TreeMap<>();
    Map<String, Long> porArquetipo = new TreeMap<>();
    for (Cliente c : todos) {
      porPerfil.merge(c.getPerfilCrediticio(), 1L, Long::sum);
      porCategoria.merge(c.getCategoria(), 1L, Long::sum);
      porArquetipo.merge(c.getArchetype(), 1L, Long::sum);
    }
    out.put("clientes", Map.of("total", todos.size(), "porPerfil", porPerfil, "porCategoria", porCategoria, "porArquetipo", porArquetipo));

    int aDias = 0;
    int conSaldo = 0;
    int diasAviso = props.push().diasAviso();
    for (Cliente c : todos) {
      for (Credito cr : creditos.findByClienteIdOrderByKindAscIdAsc(c.getId())) {
        if (motor.saldoCiclo(cr, hoy).signum() <= 0) continue;
        conSaldo++;
        long dias = CalendarioPagos.corteSiguiente(cr.getDiaCorte(), hoy).toEpochDay() - hoy.toEpochDay();
        if (dias >= 0 && dias <= diasAviso) aDias++;
      }
    }
    out.put("cobranzaPreventiva", Map.of("creditosConSaldoEnCiclo", conSaldo, "aDiasDelCorte", aDias, "diasAviso", diasAviso,
        "apartadosActivos", apartados.findByEstado(Apartado.ACTIVO).size(), "citasAgendadas", citas.countByStatus(Cita.AGENDADA)));

    long acuerdo = sesiones.countByResultado(ChatSesion.ACUERDO);
    long negativa = sesiones.countByResultado(ChatSesion.NEGATIVA);
    long siguiente = sesiones.countByResultado(ChatSesion.SIGUIENTE_PASO);
    long escalado = sesiones.countByResultado(ChatSesion.ESCALADO);
    long enCurso = sesiones.countByResultado(ChatSesion.EN_CURSO);
    long total = acuerdo + negativa + siguiente + escalado + enCurso;
    long cerradas = total - enCurso;
    Map<String, Object> chat = new LinkedHashMap<>();
    chat.put("total", total);
    chat.put("acuerdo", acuerdo);
    chat.put("negativa", negativa);
    chat.put("siguientePaso", siguiente);
    chat.put("escalado", escalado);
    chat.put("enCurso", enCurso);
    chat.put("pctResueltosSinEscalar", cerradas == 0 ? 0 : Math.round(100.0 * (acuerdo + negativa + siguiente) / cerradas));
    chat.put("pctCierresNoAmbiguos", cerradas == 0 ? 0 : 100);
    out.put("chat", chat);

    out.put("push", Map.of("enviados", notificaciones.countByEstado(NotificacionEnviada.ENVIADO),
        "simulados", notificaciones.countByEstado(NotificacionEnviada.SIMULADO),
        "errores", notificaciones.countByEstado(NotificacionEnviada.ERROR),
        "ultimas24h", notificaciones.countByFechaEnvioAfter(LocalDateTime.now().minusHours(24))));
    out.put("ia", router.metricas());
    return out;
  }
}
