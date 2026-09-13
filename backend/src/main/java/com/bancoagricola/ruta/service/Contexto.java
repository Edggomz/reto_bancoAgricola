package com.bancoagricola.ruta.service;

import com.bancoagricola.ruta.domain.*;
import com.bancoagricola.ruta.error.NotFoundException;
import com.bancoagricola.ruta.repository.Repositorios;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Carga en una sola pasada todo lo que un cliente tiene con el banco. */
@Service
public class Contexto {
  private final Repositorios.Clientes clientes;
  private final Repositorios.Asesores asesores;
  private final Repositorios.Cuentas cuentas;
  private final Repositorios.Creditos creditos;
  private final Repositorios.PlanesFecha planes;
  private final Repositorios.Apartados apartados;
  private final Repositorios.Autopagos autopagos;

  public Contexto(Repositorios.Clientes clientes, Repositorios.Asesores asesores, Repositorios.Cuentas cuentas,
                  Repositorios.Creditos creditos, Repositorios.PlanesFecha planes, Repositorios.Apartados apartados,
                  Repositorios.Autopagos autopagos) {
    this.clientes = clientes;
    this.asesores = asesores;
    this.cuentas = cuentas;
    this.creditos = creditos;
    this.planes = planes;
    this.apartados = apartados;
    this.autopagos = autopagos;
  }

  public record Datos(Cliente cliente, Asesor asesor, List<Cuenta> cuentas, List<Credito> creditos,
                      Map<String, PlanFechaCobro> planes, Map<String, Apartado> apartadosActivos,
                      Map<String, Autopago> autopagos) {

    /** Cuenta propia desde la que se aparta y se paga (solo cuentas de este banco). */
    public Optional<Cuenta> origen() {
      return cuentas.stream().filter(Cuenta::isPrimarySource).findFirst().or(() -> cuentas.stream().findFirst());
    }

    /**
     * Crédito con cuota mensual al que se le mueve la fecha de cobro: el que ya
     * tiene fecha nueva; si no, el crédito personal; si no, cualquiera con cuota.
     */
    public Optional<Credito> principalConCuota() {
      return creditos.stream().filter(c -> c.tieneCuota() && planes.containsKey(c.getId())).findFirst()
          .or(() -> creditos.stream().filter(c -> c.tieneCuota() && c.isApartable() && Credito.PERSONAL.equals(c.getKind())).findFirst())
          .or(() -> creditos.stream().filter(c -> c.tieneCuota() && c.isApartable()).findFirst())
          .or(() -> creditos.stream().filter(Credito::tieneCuota).findFirst());
    }

    /** Crédito que se aparta por defecto: el que ya tiene ruta; si no, el de cuota; si no, la tarjeta apartable. */
    public Optional<Credito> principalApartable() {
      return creditos.stream().filter(c -> c.isApartable() && apartadosActivos.containsKey(c.getId())).findFirst()
          .or(() -> principalConCuota().filter(Credito::isApartable))
          .or(() -> creditos.stream().filter(Credito::isApartable).findFirst());
    }

    public List<Credito> apartables() {
      return creditos.stream().filter(Credito::isApartable).toList();
    }

    public Optional<Credito> credito(String id) {
      return creditos.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public Optional<PlanFechaCobro> plan() {
      return principalConCuota().map(c -> planes.get(c.getId()));
    }

    public Optional<PlanFechaCobro> plan(Credito c) {
      return Optional.ofNullable(planes.get(c.getId()));
    }

    public Optional<Apartado> apartadoActivo() {
      return principalApartable().map(c -> apartadosActivos.get(c.getId()))
          .or(() -> apartadosActivos.values().stream().findFirst());
    }

    public Optional<Apartado> apartadoActivo(Credito c) {
      return Optional.ofNullable(apartadosActivos.get(c.getId()));
    }

    public List<String> creditoIds() {
      return creditos.stream().map(Credito::getId).toList();
    }

    public boolean rutaActiva() {
      return !apartadosActivos.isEmpty();
    }
  }

  public Cliente cliente(String clienteId) {
    return clientes.findById(clienteId).filter(Cliente::isActivo)
        .orElseThrow(() -> new NotFoundException("No encontramos tu perfil."));
  }

  public Datos cargar(String clienteId) {
    Cliente cliente = cliente(clienteId);
    Asesor asesor = asesores.findById(cliente.getAsesorId()).orElse(null);
    List<Cuenta> cts = cuentas.findByClienteIdOrderByPrimarySourceDescCreatedAtAsc(clienteId);
    List<Credito> crs = creditos.findByClienteIdOrderByKindAscIdAsc(clienteId);
    List<String> ids = crs.stream().map(Credito::getId).toList();
    Map<String, PlanFechaCobro> pl = new HashMap<>();
    Map<String, Apartado> ap = new HashMap<>();
    Map<String, Autopago> au = new HashMap<>();
    if (!ids.isEmpty()) {
      planes.findByCreditoIdInOrderByCreatedAtDesc(ids).forEach(p -> pl.putIfAbsent(p.getCreditoId(), p));
      apartados.findByCreditoIdInAndEstadoOrderByCreatedAtDesc(ids, Apartado.ACTIVO).forEach(a -> ap.putIfAbsent(a.getCreditoId(), a));
      autopagos.findByCreditoIdInAndActiveTrueOrderByCreatedAtDesc(ids).forEach(a -> au.putIfAbsent(a.getCreditoId(), a));
    }
    return new Datos(cliente, asesor, cts, crs, pl, ap, au);
  }
}
