package service;

import dto.PlatoRequest;
import model.Categoria;
import model.Plato;
import repository.firestore.FirestorePlatoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class PlatoService {

    private final FirestorePlatoRepository repository;

    public PlatoService(FirestorePlatoRepository repository) {
        this.repository = repository;
    }

    public Plato create(PlatoRequest request) {
        validate(request);

        Plato plato = new Plato(
                null,
                request.nombre.trim(),
                Categoria.valueOf(request.categoria.trim()),
                request.descripcion.trim(),
                new BigDecimal(request.precio.trim()),
                request.estaActivo,
                request.imagen.trim()
        );

        return repository.save(plato);
    }

    public List<Plato> findAll() {
        return repository.findAll();
    }

    public PagedResult<Plato> findPage(int limit, String cursor) {
        List<Plato> items = repository.findPage(limit, cursor);
        String nextCursor = items.size() == limit ? items.get(items.size() - 1).id() : null;
        return new PagedResult<>(items, nextCursor, limit);
    }

    public List<Plato> findActivos() {
        return repository.findByEstaActivo(true);
    }

    public Optional<Plato> findById(String id) {
        return repository.findById(id);
    }

    public Plato update(String id, PlatoRequest request) {
        validate(request);

        Plato actualizado = new Plato(
                id,
                request.nombre.trim(),
                Categoria.valueOf(request.categoria.trim()),
                request.descripcion.trim(),
                new BigDecimal(request.precio.trim()),
                request.estaActivo,
                request.imagen.trim()
        );

        return repository.update(id, actualizado);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(PlatoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }

        if (request.nombre == null || request.nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (request.categoria == null || request.categoria.isBlank()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }

        if (request.descripcion == null || request.descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción es obligatoria");
        }

        if (request.precio == null || request.precio.isBlank()) {
            throw new IllegalArgumentException("El precio es obligatorio");
        }

        if (request.estaActivo == null) {
            throw new IllegalArgumentException("El estado activo es obligatorio");
        }

        if (request.imagen == null || request.imagen.isBlank()) {
            throw new IllegalArgumentException("La imagen es obligatoria");
        }

        try {
            Categoria.valueOf(request.categoria.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("La categoría no es válida");
        }

        try {
            BigDecimal precio = new BigDecimal(request.precio.trim());
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio no puede ser negativo");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El precio no tiene un formato válido");
        }
    }
}
