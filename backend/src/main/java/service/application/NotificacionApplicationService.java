package service.application;

import model.Cuenta;
import model.Notificacion;
import model.Orden;
import model.OrdenEstado;
import model.TipoNotificacion;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NotificacionApplicationService {
    private final NotificacionRepository notificacionRepository;
    private final CuentaRepository cuentaRepository;
    private final OrdenApplicationService ordenApplicationService;

    public NotificacionApplicationService(
            NotificacionRepository notificacionRepository,
            CuentaRepository cuentaRepository,
            OrdenApplicationService ordenApplicationService
    ) {
        this.notificacionRepository = notificacionRepository;
        this.cuentaRepository = cuentaRepository;
        this.ordenApplicationService = ordenApplicationService;
    }

    public Notificacion crearNotificacionAtencion(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Optional<Notificacion> activa = notificacionRepository.findActiveAtencionByCuentaId(cuentaId);
        if (activa.isPresent()) {
            return activa.get();
        }

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Atencion,
                false,
                Instant.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );

        return notificacionRepository.saveWithDedup(notificacion);
    }

    public Optional<Notificacion> obtenerNotificacionAtencionActiva(String cuentaId) {
        return notificacionRepository.findActiveAtencionByCuentaId(cuentaId);
    }

    public Notificacion crearNotificacionPedidoListo(
            String cuentaId,
            String ordenId,
            String nombreItem,
            String categoriaItem
    ) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Recoger,
                false,
                Instant.now(),
                ordenId,
                nombreItem,
                categoriaItem,
                false,
                null,
                null,
                null
        );

        return notificacionRepository.save(notificacion);
    }

    public List<Notificacion> obtenerNotificacionesPendientes() {
        return notificacionRepository.findByLeida(false);
    }

    public List<Notificacion> obtenerNotificacionesDeCuenta(String cuentaId) {
        return notificacionRepository.findByCuentaId(cuentaId);
    }

    public List<Notificacion> obtenerNotificacionesPorTipo(TipoNotificacion tipo) {
        return notificacionRepository.findByTipoNotificacion(tipo);
    }

    public List<Notificacion> obtenerTodasLasAtencionesActivas() {
        return notificacionRepository.findByTipoAndLeida(TipoNotificacion.Atencion, false);
    }

    public void limpiarNotificacionesEstancadas() {
        Instant haceCincoMinutos = Instant.now().minusSeconds(5 * 60);

        notificacionRepository.findEnCursoNoLeidas().stream()
                .filter(n -> n.fechaEnCurso() != null)
                .filter(n -> n.fechaEnCurso().isBefore(haceCincoMinutos))
                .forEach(n -> desasignarYReenviarNotificacion(n.id()));
    }

    public Notificacion marcarNotificacionEnCurso(
            String notificacionId,
            String camareroUid,
            String camareroNombre
    ) {
        String nombreLimpio = camareroNombre == null || camareroNombre.isBlank()
                ? "Camarero"
                : camareroNombre.trim();

        return notificacionRepository.marcarEnCursoSiDisponible(
                        notificacionId,
                        camareroUid,
                        nombreLimpio,
                        notificacion -> new Notificacion(
                                notificacion.id(),
                                notificacion.cuenta(),
                                notificacion.tipo(),
                                false,
                                notificacion.fecha(),
                                notificacion.ordenId(),
                                notificacion.nombreItem(),
                                notificacion.categoriaItem(),
                                true,
                                camareroUid,
                                nombreLimpio,
                                Instant.now()
                        )
                )
                .orElseThrow(() -> new IllegalStateException("Esta notificación ya la ha cogido otro compañero"));
    }

    public Notificacion marcarNotificacionLeida(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                true,
                notificacion.fecha(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                notificacion.enCurso(),
                notificacion.camareroUid(),
                notificacion.camareroNombre(),
                notificacion.fechaEnCurso()
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    public Notificacion marcarNotificacionCompletada(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        if (notificacion.tipo() == TipoNotificacion.Recoger) {
            String ordenId = notificacion.ordenId();
            if (ordenId == null || ordenId.isBlank()) {
                ordenId = resolverOrdenIdRecoger(notificacion);
            }

            if (ordenId != null && !ordenId.isBlank()) {
                ordenApplicationService.marcarOrdenEntregada(ordenId);
            }
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                true,
                notificacion.fecha(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                notificacion.enCurso(),
                notificacion.camareroUid(),
                notificacion.camareroNombre(),
                notificacion.fechaEnCurso()
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    private String resolverOrdenIdRecoger(Notificacion notificacion) {
        if (notificacion == null || notificacion.cuenta() == null || notificacion.cuenta().id() == null) {
            return null;
        }

        String cuentaId = notificacion.cuenta().id();
        String nombreItem = notificacion.nombreItem() != null ? notificacion.nombreItem().trim() : "";

        List<Orden> candidatas = ordenApplicationService.obtenerPlatosActivosSala().stream()
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Listo)
                .filter(orden -> orden.pedido() != null
                        && orden.pedido().cuenta() != null
                        && cuentaId.equals(orden.pedido().cuenta().id()))
                .toList();

        if (candidatas.isEmpty()) {
            return null;
        }

        if (!nombreItem.isBlank()) {
            Optional<Orden> porNombre = candidatas.stream()
                    .filter(orden -> orden.plato() != null
                            && orden.plato().nombre() != null
                            && nombreItem.equalsIgnoreCase(orden.plato().nombre().trim()))
                    .findFirst();
            if (porNombre.isPresent()) {
                return porNombre.get().id();
            }
        }

        return candidatas.get(0).id();
    }

    public Notificacion desasignarYReenviarNotificacion(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                false,
                Instant.now(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                false,
                null,
                null,
                null
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    public void eliminarNotificacionesRecogerDeOrden(String ordenId) {
        if (ordenId == null || ordenId.isBlank()) {
            return;
        }

        notificacionRepository.findByOrdenId(ordenId).stream()
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .forEach(notificacion -> notificacionRepository.deleteById(notificacion.id()));
    }

    public void marcarNotificacionesRecogerComoLeidasDeCuenta(String cuentaId) {
        if (cuentaId == null || cuentaId.isBlank()) {
            return;
        }

        notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(notificacion -> !notificacion.leida())
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .forEach(notificacion -> {
                    Notificacion actualizada = new Notificacion(
                            notificacion.id(),
                            notificacion.cuenta(),
                            notificacion.tipo(),
                            true,
                            notificacion.fecha(),
                            notificacion.ordenId(),
                            notificacion.nombreItem(),
                            notificacion.categoriaItem(),
                            notificacion.enCurso(),
                            notificacion.camareroUid(),
                            notificacion.camareroNombre(),
                            notificacion.fechaEnCurso()
                    );

                    notificacionRepository.update(notificacion.id(), actualizada);
                });
    }
}
