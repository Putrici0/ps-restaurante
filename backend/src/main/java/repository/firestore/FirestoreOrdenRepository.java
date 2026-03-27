package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import repository.interfaces.OrdenRepository;

import java.util.List;
import java.util.Optional;

public class FirestoreOrdenRepository implements OrdenRepository {

    private final Firestore db;

    public FirestoreOrdenRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public List<Orden> findByPedido(Pedido pedido) {
        return List.of();
    }

    @Override
    public List<Orden> findByEstado(OrdenEstado estado) {
        return List.of();
    }

    @Override
    public List<Orden> findAll() {
        return List.of();
    }

    @Override
    public Optional<Orden> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Orden save(Orden entity) {
        return null;
    }

    @Override
    public Orden update(String s, Orden entity) {
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
