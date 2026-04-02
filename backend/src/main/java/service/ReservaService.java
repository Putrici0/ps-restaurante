package service;

import dto.ReservaRequest;
import model.Reserva;
import repository.firestore.FirestoreReservaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ReservaService {

    private final FirestoreReservaRepository repository;

    public ReservaService(FirestoreReservaRepository repository) {
        this.repository = repository;
    }

    public Reserva create(ReservaRequest request) {
        validate(request);

        Reserva reserva = new Reserva(
                null,
                request.nombre.trim(),
                Instant.parse(request.fecha.trim()),
                request.capacidad,
                Instant.now()
        );

        return repository.save(reserva);
    }

    public List<Reserva> findAll() {
        return repository.findAll();
    }

    public Optional<Reserva> findById(String id) {
        return repository.findById(id);
    }

    public Reserva update(String id, ReservaRequest request) {
        validate(request);

        Reserva original = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La reserva no existe"));

        Reserva actualizada = new Reserva(
                id,
                request.nombre.trim(),
                Instant.parse(request.fecha.trim()),
                request.capacidad,
                original.fechaCreacion()
        );

        return repository.update(id, actualizada);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(ReservaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.nombre == null || request.nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (request.fecha == null || request.fecha.isBlank()) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        if (request.capacidad == null) {
            throw new IllegalArgumentException("La capacidad es obligatoria");
        }
        if (request.capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor que 0");
        }

        try {
            Instant.parse(request.fecha.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("La fecha debe tener formato ISO-8601 válido");
        }
    }
}