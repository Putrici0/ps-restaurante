package service;

import dto.NotificacionRequest;
import model.Cuenta;
import model.Notificacion;
import model.TipoNotificacion;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestoreNotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NotificacionService {

    private final FirestoreNotificacionRepository repository;
    private final FirestoreCuentaRepository cuentaRepository;

    public NotificacionService(FirestoreNotificacionRepository repository,
                               FirestoreCuentaRepository cuentaRepository) {
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