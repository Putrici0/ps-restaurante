package service.application;

import model.Cuenta;
import model.Notificacion;
import model.TipoNotificacion;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.List;

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

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Atencion,
                false,
                Instant.now()
        );

        return notificacionRepository.save(notificacion);
    }

    public Notificacion crearNotificacionPedidoListo(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Recoger,
                false,
                Instant.now()
        );

        return notificacionRepository.save(notificacion);
    }

    public List<Notificacion> obtenerNotificacionesPendientes() {
        return notificacionRepository.findAll().stream()
                .filter(notificacion -> !notificacion.leida())
                .toList();
    }

    public List<Notificacion> obtenerNotificacionesDeCuenta(String cuentaId) {
        return notificacionRepository.findAll().stream()
                .filter(notificacion -> notificacion.cuenta() != null)
                .filter(notificacion -> notificacion.cuenta().id() != null)
                .filter(notificacion -> notificacion.cuenta().id().equals(cuentaId))
                .toList();
    }

    public List<Notificacion> obtenerNotificacionesPorTipo(TipoNotificacion tipo) {
        return notificacionRepository.findAll().stream()
                .filter(notificacion -> notificacion.tipo() == tipo)
                .toList();
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
                notificacion.fecha()
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }
}