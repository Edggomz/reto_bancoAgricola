package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.Oferta;
import com.bancoagricola.ruta.domain.ProductoActivado;
import com.bancoagricola.ruta.dto.App;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * «Tienes productos disponibles» (02): lo que se puede activar desde el inicio.
 * Un producto desaparece en cuanto se activa: cambiar fecha (al confirmar la
 * fecha o al quedar la ruta activa) y el depósito a plazo (al activarlo).
 */
@Service
public class ProductoService {
  private static final Map<String, String> ID_POR_KEY = Map.of("change-date", "cambiar-fecha", "term-deposit", "deposito-plazo");
  private static final Map<String, String> ILUSTRACION = Map.of("change-date", "calendario", "term-deposit", "monedas");

  private final Contexto contexto;
  private final Repositorios.Ofertas ofertas;
  private final Repositorios.ProductosActivados activados;
  private final CopyService copy;
  private final AvisoService avisos;
  private final AuditoriaService auditoria;

  public ProductoService(Contexto contexto, Repositorios.Ofertas ofertas, Repositorios.ProductosActivados activados,
                         CopyService copy, AvisoService avisos, AuditoriaService auditoria) {
    this.contexto = contexto;
    this.ofertas = ofertas;
    this.activados = activados;
    this.copy = copy;
    this.avisos = avisos;
    this.auditoria = auditoria;
  }

  public List<App.Producto> disponibles(Contexto.Datos datos) {
    Set<String> yaActivados = activados.findByClienteId(datos.cliente().getId()).stream()
        .map(ProductoActivado::getOfertaId).collect(Collectors.toSet());
    boolean fechaResuelta = datos.plan().isPresent() || datos.rutaActiva() || datos.principalConCuota().isEmpty();
    List<App.Producto> out = new ArrayList<>();
    for (Oferta o : ofertas.findByActivoTrueOrderByOrdenAsc()) {
      if (yaActivados.contains(o.getId())) continue;
      if ("change-date".equals(o.getOkey()) && fechaResuelta) continue;
      String id = ID_POR_KEY.getOrDefault(o.getOkey(), o.getOkey());
      String condiciones = "term-deposit".equals(o.getOkey()) ? copy.texto("producto.deposito.condiciones") : null;
      out.add(new App.Producto(id, o.getTitle(), o.getSubtitle(), ILUSTRACION.getOrDefault(o.getOkey(), "monedas"), condiciones));
    }
    return out;
  }

  @Transactional
  public App.Producto activar(String clienteId, String productoId) {
    Contexto.Datos datos = contexto.cargar(clienteId);
    String okey = ID_POR_KEY.entrySet().stream().filter(e -> e.getValue().equals(productoId)).map(Map.Entry::getKey)
        .findFirst().orElse(productoId);
    if ("change-date".equals(okey)) {
      throw new IllegalArgumentException("La fecha de cobro se cambia eligiendo el día en «Cambiar fecha de cobro».");
    }
    Oferta o = ofertas.findByOkey(okey).filter(Oferta::isActivo)
        .orElseThrow(() -> new IllegalArgumentException("Ese producto no está disponible."));
    if (!activados.existsByClienteIdAndOfertaId(clienteId, o.getId())) {
      ProductoActivado p = new ProductoActivado();
      p.setId("prod-" + UUID.randomUUID());
      p.setClienteId(clienteId);
      p.setOfertaId(o.getId());
      p.setDetalle(o.getTitle());
      activados.save(p);
      auditoria.info(AuditoriaService.APP, "producto.activado", clienteId, o.getId(), "Activó " + o.getTitle(), null);
      if ("term-deposit".equals(okey)) {
        avisos.emitir(clienteId, AvisoService.CONFIRM, copy.texto("aviso.deposito.titulo"),
            copy.texto("aviso.deposito.cuerpo"), null, null, "producto-activado");
      }
    }
    return new App.Producto(productoId, o.getTitle(), o.getSubtitle(), ILUSTRACION.getOrDefault(okey, "monedas"), null);
  }
}
