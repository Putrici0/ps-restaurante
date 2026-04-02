package service;

import dto.MesaRequest;
import model.Mesa;
import repository.firestore.FirestoreMesaRepository;

import java.util.List;
import java.util.Optional;

public class MesaService {

    private final FirestoreMesaRepository repository;

    public MesaService(FirestoreMesaRepository repository) {
        this.repository = repository;
    }

    public Mesa create(MesaRequest request) {
        validate(request);

        Mesa mesa = new Mesa(
                null,
                request.capacidad
        );

        return repository.save(mesa);
    }

    public List<Mesa> findAll() {
        return repository.findAll();
    }

    public Optional<Mesa> findById(String id) {
        return repository.findById(id);
    }

    public Mesa update(String id, MesaRequest request) {
        validate(request);

        Mesa actualizada = new Mesa(
                id,
                request.capacidad
        );

        return repository.update(id, actualizada);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(MesaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.capacidad == null) {
            throw new IllegalArgumentException("La capacidad es obligatoria");
        }
        if (request.capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor que 0");
        }
    }
}