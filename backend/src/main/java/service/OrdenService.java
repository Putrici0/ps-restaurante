package service;

import dto.OrdenRequest;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.Plato;
import repository.firestore.FirestoreOrdenRepository;
import repository.firestore.FirestorePedidoRepository;
import repository.firestore.FirestorePlatoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class OrdenService {

    private final FirestoreOrdenRepository repository;
    private final FirestorePedidoRepository pedidoRepository;
    private final FirestorePlatoRepository platoRepository;

    public OrdenService(FirestoreOrdenRepository repository,
                        FirestorePedidoRepository pedidoRepository,
                        FirestorePlatoRepository platoRepository) {
        this.repository = repository;
        this.pedidoRepository = pedidoRepository;
        this.platoRepository = platoRepository;
    }

    public Orden create(OrdenRequest request) {
        validate(request);

        Pedido pedido = pedidoRepository.findById(request.pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        Plato plato = platoRepository.findById(request.platoId)
                .orElseThrow(() -> new IllegalArgumentException("El plato no existe"));

        Orden orden = new Orden(
                null,
                pedido,
                plato,
                new BigDecimal(request.precio.trim()),
                OrdenEstado.valueOf(request.estado.trim()),
                Instant.now(),
                request.detalles == null ? "" : request.detalles.trim()
        );

        return repository.save(orden);
    }

    public List<Orden> findAll() {
        return repository.findAll();
    }

    public Optional<Orden> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(OrdenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.pedidoId == null || request.pedidoId.isBlank()) {
            throw new IllegalArgumentException("El pedido es obligatorio");
        }
        if (request.platoId == null || request.platoId.isBlank()) {
            throw new IllegalArgumentException("El plato es obligatorio");
        }
        if (request.precio == null || request.precio.isBlank()) {
            throw new IllegalArgumentException("El precio es obligatorio");
        }
        if (request.estado == null || request.estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        try {
            new BigDecimal(request.precio.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El precio no tiene un formato válido");
        }

        try {
            OrdenEstado.valueOf(request.estado.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El estado de la orden no es válido");
        }
    }
}