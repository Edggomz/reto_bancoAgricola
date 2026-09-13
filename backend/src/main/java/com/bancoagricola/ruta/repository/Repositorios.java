package com.bancoagricola.ruta.repository;

import com.bancoagricola.ruta.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorios Spring Data de todas las tablas, en un solo lugar para ver de un
 * vistazo qué consultas hace la aplicación. Se registran con
 * considerNestedRepositories (ver PersistenceConfig).
 */
public final class Repositorios {
  private Repositorios() {}

  public interface ParametrosApp extends JpaRepository<ParametroApp, String> {}

  public interface Frecuencias extends JpaRepository<CatalogoFrecuencia, String> {
    List<CatalogoFrecuencia> findByActivoTrueOrderByOrdenAsc();

    Optional<CatalogoFrecuencia> findBySlug(String slug);
  }

  public interface FrecuenciaDias extends JpaRepository<CatalogoFrecuenciaDia, CatalogoFrecuenciaDia.Pk> {
    List<CatalogoFrecuenciaDia> findByFrecuenciaIdOrderByIdxAsc(String frecuenciaId);
  }

  public interface Partes extends JpaRepository<CatalogoPartes, Integer> {
    List<CatalogoPartes> findByActivoTrueOrderByOrdenAsc();
  }

  public interface DiasAsesoria extends JpaRepository<CatalogoDiaAsesoria, String> {
    List<CatalogoDiaAsesoria> findByActivoTrueOrderByOrdenAsc();
  }

  public interface HorasAsesoria extends JpaRepository<CatalogoHoraAsesoria, String> {
    List<CatalogoHoraAsesoria> findByActivoTrueOrderByOrdenAsc();
  }

  public interface OpcionesChat extends JpaRepository<CatalogoChatOpcion, String> {
    List<CatalogoChatOpcion> findByActivoTrueOrderByOrdenAsc();
  }

  public interface Ofertas extends JpaRepository<Oferta, String> {
    List<Oferta> findByActivoTrueOrderByOrdenAsc();

    Optional<Oferta> findByOkey(String okey);
  }

  public interface ProductosActivados extends JpaRepository<ProductoActivado, String> {
    List<ProductoActivado> findByClienteId(String clienteId);

    boolean existsByClienteIdAndOfertaId(String clienteId, String ofertaId);
  }

  public interface OfertasApertura extends JpaRepository<OfertaApertura, String> {
    Optional<OfertaApertura> findFirstByActivoTrueOrderByIdAsc();
  }

  public interface CondicionesApertura extends JpaRepository<OfertaAperturaCondicion, OfertaAperturaCondicion.Pk> {
    List<OfertaAperturaCondicion> findByOfertaIdOrderByIdxAsc(String ofertaId);
  }

  public interface DocumentosApertura extends JpaRepository<OfertaAperturaDocumento, String> {
    List<OfertaAperturaDocumento> findByOfertaIdOrderByOrdenAsc(String ofertaId);
  }

  public interface Asesores extends JpaRepository<Asesor, String> {}

  public interface Temas extends JpaRepository<AsesoriaTema, String> {
    List<AsesoriaTema> findByActivoTrueOrderByOrdenAsc();

    Optional<AsesoriaTema> findByProductoTipo(String productoTipo);
  }

  public interface Clientes extends JpaRepository<Cliente, String> {
    Optional<Cliente> findByUsername(String username);

    List<Cliente> findByActivoTrue();
  }

  public interface Tokens extends JpaRepository<SesionToken, String> {
    long deleteByExpiresAtBefore(LocalDateTime limite);
  }

  public interface Cuentas extends JpaRepository<Cuenta, String> {
    List<Cuenta> findByClienteIdOrderByPrimarySourceDescCreatedAtAsc(String clienteId);

    boolean existsByNumberFull(String numberFull);
  }

  public interface Creditos extends JpaRepository<Credito, String> {
    List<Credito> findByClienteIdOrderByKindAscIdAsc(String clienteId);
  }

  public interface PlanesFecha extends JpaRepository<PlanFechaCobro, String> {
    List<PlanFechaCobro> findByCreditoIdInOrderByCreatedAtDesc(Collection<String> creditoIds);
  }

  public interface Apartados extends JpaRepository<Apartado, String> {
    List<Apartado> findByCreditoIdAndEstado(String creditoId, String estado);

    List<Apartado> findByCreditoIdInAndEstadoOrderByCreatedAtDesc(Collection<String> creditoIds, String estado);

    List<Apartado> findByEstado(String estado);

    List<Apartado> findByEstadoAndFechaPagoLessThanEqual(String estado, LocalDate fechaPago);
  }

  public interface ApartadoCuotas extends JpaRepository<ApartadoCuota, ApartadoCuota.Pk> {
    List<ApartadoCuota> findByApartadoIdOrderByIdxAsc(String apartadoId);

    List<ApartadoCuota> findByFechaLessThanEqualAndEstadoOrderByFechaAsc(LocalDate fecha, String estado);
  }

  public interface Autopagos extends JpaRepository<Autopago, String> {
    List<Autopago> findByCreditoIdAndActiveTrue(String creditoId);

    List<Autopago> findByCreditoIdInAndActiveTrueOrderByCreatedAtDesc(Collection<String> creditoIds);
  }

  public interface Avisos extends JpaRepository<Aviso, String> {
    List<Aviso> findByClienteIdOrderByCreatedAtDesc(String clienteId);

    List<Aviso> findByClienteIdAndReadFlagFalse(String clienteId);

    long countByClienteIdAndReadFlagFalse(String clienteId);
  }

  public interface Records extends JpaRepository<RecordPago, String> {}

  public interface Hitos extends JpaRepository<RecordHito, RecordHito.Pk> {
    List<RecordHito> findByClienteIdOrderByIdxAsc(String clienteId);
  }

  public interface Sumandos extends JpaRepository<RecordSumando, String> {
    List<RecordSumando> findByClienteIdOrderByOrdenAsc(String clienteId);
  }

  public interface Choques extends JpaRepository<ShockContext, String> {}

  public interface Citas extends JpaRepository<Cita, String> {
    List<Cita> findByClienteIdOrderByCreatedAtDesc(String clienteId);

    long countByStatus(String status);
  }

  public interface Sesiones extends JpaRepository<ChatSesion, String> {
    long countByResultado(String resultado);

    List<ChatSesion> findByClienteIdOrderByCreatedAtDesc(String clienteId);
  }

  public interface Mensajes extends JpaRepository<ChatMensaje, String> {
    List<ChatMensaje> findBySesionIdOrderByOrdenAsc(String sesionId);

    long countBySesionId(String sesionId);
  }

  public interface QuickReplies extends JpaRepository<ChatQuickReply, ChatQuickReply.Pk> {
    List<ChatQuickReply> findByMensajeIdOrderByIdxAsc(String mensajeId);
  }

  public interface Transacciones extends JpaRepository<Transaccion, String> {
    List<Transaccion> findByCreditoIdInAndFechaAfterOrderByFechaAsc(Collection<String> creditoIds, LocalDate desde);

    List<Transaccion> findByCreditoIdAndFechaGreaterThanAndFechaLessThanEqual(String creditoId, LocalDate desde, LocalDate hasta);
  }

  public interface Dispositivos extends JpaRepository<Dispositivo, String> {
    Optional<Dispositivo> findByPushToken(String pushToken);

    List<Dispositivo> findByClienteIdAndActivoTrue(String clienteId);
  }

  public interface Notificaciones extends JpaRepository<NotificacionEnviada, String> {
    boolean existsByCreditoIdAndTipoAndCorteFechaAndEstadoIn(String creditoId, String tipo, LocalDate corteFecha, Collection<String> estados);

    List<NotificacionEnviada> findTop50ByOrderByFechaEnvioDesc();

    long countByEstado(String estado);

    long countByFechaEnvioAfter(LocalDateTime desde);
  }

  public interface LlamadasIa extends JpaRepository<IaLlamada, String> {
    long countByServicio(String servicio);

    long countByFallbackTrue();

    long countByExitoFalse();

    long countByProveedor(String proveedor);

    long countByGuardrailIsNotNull();

    @Query("select coalesce(avg(l.latenciaMs), 0) from IaLlamada l where l.exito = true")
    Double latenciaPromedio();
  }
}
