package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Asesor;
import com.bancoagricola.ruta.domain.AsesoriaTema;
import com.bancoagricola.ruta.domain.CatalogoDiaAsesoria;
import com.bancoagricola.ruta.domain.CatalogoHoraAsesoria;
import com.bancoagricola.ruta.domain.Cita;
import com.bancoagricola.ruta.domain.Credito;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asistencia con la asesora de siempre. Escalar no es fracaso: es la salida
 * honesta cuando el chat no resuelve. Solo el canal depende de la tarjeta.
 */
@Service
public class CitaService {
  private final Contexto contexto;
  private final Repositorios.Citas citas;
  private final Repositorios.DiasAsesoria dias;
  private final Repositorios.HorasAsesoria horas;
  private final Repositorios.Temas temas;
  private final CopyService copy;
  private final AvisoService avisos;

  public CitaService(Contexto contexto, Repositorios.Citas citas, Repositorios.DiasAsesoria dias,
                     Repositorios.HorasAsesoria horas, Repositorios.Temas temas, CopyService copy, AvisoService avisos) {
    this.contexto = contexto;
    this.citas = citas;
    this.dias = dias;
    this.horas = horas;
    this.temas = temas;
    this.copy = copy;
    this.avisos = avisos;
  }

  /** Agenda con el primer día y hora disponibles del catálogo. */
  @Transactional
  public Cita agendar(String clienteId, String creditoId, String source) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    Asesor asesor = datos.asesor();
    if (asesor == null) throw new IllegalStateException("El cliente no tiene asesora asignada.");
    Credito credito = (creditoId == null ? datos.principalApartable() : datos.credito(creditoId)).orElse(null);
    String tipo = credito == null ? "other" : credito.esTarjeta() ? "card" : "personal";
    AsesoriaTema tema = temas.findByProductoTipo(tipo).or(() -> temas.findByProductoTipo("other"))
        .orElseThrow(() -> new IllegalStateException("Falta el catálogo ASESORIA_TEMA."));
    List<CatalogoDiaAsesoria> ds = dias.findByActivoTrueOrderByOrdenAsc();
    List<CatalogoHoraAsesoria> hs = horas.findByActivoTrueOrderByOrdenAsc();
    if (ds.isEmpty() || hs.isEmpty()) throw new IllegalStateException("Faltan días u horas de asesoría en el catálogo.");
    CatalogoDiaAsesoria dia = ds.get(0);
    CatalogoHoraAsesoria hora = hs.get(0);
    String temaLabel = Fechas.minusculaInicial(tema.getLabel().replace("{producto}", credito == null ? "" : credito.getName()));

    Cita c = new Cita();
    c.setId("appt-" + UUID.randomUUID());
    c.setClienteId(clienteId);
    c.setAsesorId(asesor.getId());
    c.setTemaId(tema.getId());
    c.setProductoId(credito == null ? null : credito.getId());
    c.setDiaId(dia.getId());
    c.setHoraId(hora.getId());
    c.setWithLabel(asesor.getName());
    c.setWhenLabel(copy.render("cita.cuando", Map.of("dia", dia.getWhenLabel(), "hora", hora.getLabel())));
    c.setWhereLabel(copy.render("cita.donde", Map.of("agencia", asesor.getAgency())));
    c.setAboutLabel(copy.render("cita.sobre", Map.of("tema", temaLabel)));
    c.setConfirmationNote(copy.render("cita.nota", Map.of("asesora", asesor.primerNombre(), "tema", temaLabel)));
    c.setStatus(Cita.AGENDADA);
    c.setSource("shock".equals(source) ? "shock" : "chat");
    citas.save(c);
    avisos.emitir(clienteId, AvisoService.CONFIRM, copy.texto("aviso.cita.titulo"),
        copy.render("aviso.cita.cuerpo", Map.of("cuando", c.getWhenLabel(), "asesora", asesor.getName(),
            "donde", c.getWhereLabel(), "tema", temaLabel)), null, c.getProductoId(), "cita-agendada");
    return c;
  }
}
