package service;

import dto.PedidoRequest;
import model.Cuenta;
import model.Pedido;
import model.PedidoEstado;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestorePedidoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PedidoService {

    private final FirestorePedidoRepository repository;
    private final FirestoreCuentaRepository cuentaRepository;

    public PedidoService(FirestorePedidoRepository repository,
                         FirestoreCuentaRepository cuentaRepository) {
        this.repository = repository;
        this.cuentaRepository = cuentaRepository;
    }

    public Pedido create(PedidoRequest request) {
        validate(request);

        Cuenta cuenta = cuentaRepository.findById(request.cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Pedido pedido = new Pedido(
                null,
                cuenta,
                PedidoEstado.valueOf(request.estado.trim()),
                Instant.now()
        );

        return repository.save(pedido);
    }

    public List<Pedido> findAll() {
        return repository.findAll();
    }

    public Optional<Pedido> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(PedidoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.cuentaId == null || request.cuentaId.isBlank()) {
            throw new IllegalArgumentException("La cuenta es obligatoria");
        }
        if (request.estado == null || request.estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        try {
            PedidoEstado.valueOf(request.estado.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El estado del pedido no es válido");
        }
    }
}