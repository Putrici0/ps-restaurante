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

    public List<Cuenta> findCuentasActivas() {
        return repository.findByEstaPagada(false);
    }

    public List<Cuenta> findAll() { return repository.findAll(); }
    public Optional<Cuenta> findById(String id) { return repository.findById(id); }
    public void delete(String id) { repository.deleteById(id); }

    public Cuenta create(CuentaRequest request) {
        List<Mesa> mesas = new ArrayList<>();
        for (String mesaId : request.mesasIds) {
            Mesa mesa = mesaRepository.findById(mesaId).orElseThrow();
            mesas.add(mesa);
        }
        Cuenta cuenta = new Cuenta(null, mesas, false, Optional.empty(), Instant.now(), Optional.empty(), "", Optional.empty());
        return repository.save(cuenta);
    }
}