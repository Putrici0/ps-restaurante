package service.application;

import model.Cuenta;
import model.Notificacion;
import model.TipoNotificacion;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NotificacionApplicationService {
    private final NotificacionRepository notificacionRepository;
    private final CuentaRepository cuentaRepository;

    public NotificacionApplicationService(
            NotificacionRepository notificacionRepository,
            CuentaRepository cuentaRepository
    ) {
        this.notificacionRepository = notificacionRepository;
        this.cuentaRepository = cuentaRepository;
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
        Cuenta cuentaRef = new Cuenta(
                cuentaId,
                List.of(),
                false,
                java.util.Optional.empty(),
                Instant.EPOCH,
                java.util.Optional.empty(),
                "",
                java.util.Optional.empty()
        );
        return notificacionRepository.findByCuenta(cuentaRef);
    }

    public List<Notificacion> obtenerNotificacionesPorTipo(TipoNotificacion tipo) {
        return notificacionRepository.findByTipoNotificacion(tipo);
    }

    public List<Notificacion> obtenerTodasLasAtencionesActivas() {
        return notificacionRepository.findByTipoNotificacion(TipoNotificacion.Atencion).stream()
                .filter(n -> !n.leida())
                .toList();
        return notificacionRepository.findByTipoNotificacion(tipo);
    }

    public void limpiarNotificacionesEstancadas() {
        Instant haceCincoMinutos = Instant.now().minusSeconds(5 * 60);

        notificacionRepository.findAll().stream()
                .filter(n -> !n.leida() && n.enCurso() && n.fechaEnCurso() != null)
                .filter(n -> n.fechaEnCurso().isBefore(haceCincoMinutos))
                .forEach(n -> desasignarYReenviarNotificacion(n.id()));
    }

    public Notificacion marcarNotificacionEnCurso(
            String notificacionId,
            String camareroUid,
            String camareroNombre
    ) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        String nombreLimpio = camareroNombre == null || camareroNombre.isBlank()
                ? "Camarero"
                : camareroNombre.trim();

        Notificacion actualizada = new Notificacion(
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
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
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
        notificacionRepository.findByTipoNotificacion(TipoNotificacion.Recoger).stream()
                .filter(notificacion -> ordenId.equals(notificacion.ordenId()))
                .forEach(notificacion -> notificacionRepository.deleteById(notificacion.id()));
    }

    public void marcarNotificacionesRecogerComoLeidasDeCuenta(String cuentaId) {
        if (cuentaId == null || cuentaId.isBlank()) {
            return;
        }

        notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(notificacion -> !notificacion.leida())
        notificacionRepository.findByLeida(false).stream()
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .filter(notificacion -> notificacion.cuenta() != null)
                .filter(notificacion -> cuentaId.equals(notificacion.cuenta().id()))
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
