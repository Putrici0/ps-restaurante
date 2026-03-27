package repository.firestore;

import com.google.cloud.firestore.Firestore;
import config.FirestoreClientProvider;
import model.Cuenta;
import model.Mesa;
import repository.interfaces.CuentaRepository;

import java.util.List;
import java.util.Optional;

public class FirestoreCuentaRepository implements CuentaRepository {

    private final Firestore db;

    public FirestoreCuentaRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public Optional<Cuenta> findByMesa(Mesa mesa) {
        return Optional.empty();
    }

    @Override
    public List<Cuenta> findByPayed(Boolean payed) {
        return List.of();
    }

    @Override
    public List<Cuenta> findAll() {
        return List.of();
    }

    @Override
    public Optional<Cuenta> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Cuenta save(Cuenta entity) {
        return null;
    }

    @Override
    public Cuenta update(String s, Cuenta entity) {
        return null;
    }

    @Override
    public void deleteById(String s) {

    }

    @Override
    public boolean existsById(String s) {
        return false;
    }
}
