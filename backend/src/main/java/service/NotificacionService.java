package service;

import dto.NotificacionRequest;
import model.Cuenta;
import model.Notificacion;
import model.TipoNotificacion;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NotificacionService {

    private final NotificacionRepository repository;
    private final CuentaRepository cuentaRepository;

    public NotificacionService(NotificacionRepository repository,
                               CuentaRepository cuentaRepository) {
        this.repository = repository;
        this.cuentaRepository = cuentaRepository;
    }

    public Notificacion create(NotificacionRequest request) {
        validate(request);

        Cuenta cuenta = cuentaRepository.findById(request.cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.valueOf(request.tipo.trim()),
                request.leida,
                Instant.now()
        );

        return repository.save(notificacion);
    }

    public List<Notificacion> findAll() {
        return repository.findAll();
    }

    public Optional<Notificacion> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    // Business Methods from NotificacionApplicationService

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

        return repository.save(notificacion);
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

        return repository.save(notificacion);
    }

    public List<Notificacion> obtenerNotificacionesPendientes() {
        return repository.findAll().stream()
                .filter(notificacion -> !notificacion.leida())
                .toList();
    }

    public List<Notificacion> obtenerNotificacionesDeCuenta(String cuentaId) {
        return repository.findAll().stream()
                .filter(notificacion -> notificacion.cuenta() != null)
                .filter(notificacion -> notificacion.cuenta().id() != null)
                .filter(notificacion -> notificacion.cuenta().id().equals(cuentaId))
                .toList();
    }

    public List<Notificacion> obtenerNotificacionesPorTipo(TipoNotificacion tipo) {
        return repository.findAll().stream()
                .filter(notificacion -> notificacion.tipo() == tipo)
                .toList();
    }

    public Notificacion marcarNotificacionLeida(String notificacionId) {
        Notificacion notificacion = repository.findById(notificacionId)
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

        return repository.update(notificacion.id(), actualizada);
    }

    private void validate(NotificacionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.cuentaId == null || request.cuentaId.isBlank()) {
            throw new IllegalArgumentException("La cuenta es obligatoria");
        }
        if (request.tipo == null || request.tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo es obligatorio");
        }
        if (request.leida == null) {
            throw new IllegalArgumentException("El estado leída es obligatorio");
        }

        try {
            TipoNotificacion.valueOf(request.tipo.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El tipo de notificación no es válido");
        }
    }
}