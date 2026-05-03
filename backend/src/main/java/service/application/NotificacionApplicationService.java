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
                Instant.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );

        return notificacionRepository.save(notificacion);
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

    public void eliminarNotificacionesRecogerDeOrden(String ordenId) {
        if (ordenId == null || ordenId.isBlank()) {
            return;
        }

        notificacionRepository.findAll().stream()
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .filter(notificacion -> ordenId.equals(notificacion.ordenId()))
                .forEach(notificacion -> notificacionRepository.deleteById(notificacion.id()));
    }

    public void marcarNotificacionesRecogerComoLeidasDeCuenta(String cuentaId) {
        if (cuentaId == null || cuentaId.isBlank()) {
            return;
        }

        notificacionRepository.findAll().stream()
                .filter(notificacion -> !notificacion.leida())
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
