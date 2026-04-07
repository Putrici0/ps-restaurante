package service;

import dto.CuentaRequest;
import model.Cuenta;
import model.Mesa;
import model.Reserva;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestoreMesaRepository;
import repository.firestore.FirestoreReservaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuentaService {

    private final FirestoreCuentaRepository repository;
    private final FirestoreMesaRepository mesaRepository;
    private final FirestoreReservaRepository reservaRepository;

    public CuentaService(FirestoreCuentaRepository repository,
                         FirestoreMesaRepository mesaRepository,
                         FirestoreReservaRepository reservaRepository) {
        this.repository = repository;
        this.mesaRepository = mesaRepository;
        this.reservaRepository = reservaRepository;
    }

    public Cuenta create(CuentaRequest request) {
        validate(request);

        List<Mesa> mesas = new ArrayList<>();
        for (String mesaId : request.mesasIds) {
            Mesa mesa = mesaRepository.findById(mesaId)
                    .orElseThrow(() -> new IllegalArgumentException("La mesa con id " + mesaId + " no existe"));
            mesas.add(mesa);
        }

        Optional<Reserva> reserva = Optional.empty();
        if (request.reservaId != null && !request.reservaId.isBlank()) {
            reserva = Optional.of(
                    reservaRepository.findById(request.reservaId)
                            .orElseThrow(() -> new IllegalArgumentException("La reserva no existe"))
            );
        }

        Cuenta cuenta = new Cuenta(
                null,
                mesas,
                request.estaPagada,
                reserva,
                Instant.now(),
                Optional.empty(),
                ""
        );

        return repository.save(cuenta);
    }

    public List<Cuenta> findAll() {
        return repository.findAll();
    }

    public Optional<Cuenta> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    private void validate(CuentaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.mesasIds == null || request.mesasIds.length == 0) {
            throw new IllegalArgumentException("Debe indicarse al menos una mesa");
        }
        if (request.estaPagada == null) {
            throw new IllegalArgumentException("El estado de pago es obligatorio");
        }
    }
}