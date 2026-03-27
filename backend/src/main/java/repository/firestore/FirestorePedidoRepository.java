package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Pedido;
import model.PedidoEstado;
import repository.interfaces.PedidoRepository;

import java.util.List;
import java.util.Optional;

public class FirestorePedidoRepository implements PedidoRepository {

    private final Firestore db;

    public FirestorePedidoRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public List<Pedido> findByCuenta(Cuenta cuenta) {
        return List.of();
    }

    @Override
    public List<Pedido> findByEstado(PedidoEstado estado) {
        return List.of();
    }

    @Override
    public List<Pedido> findAll() {
        return List.of();
    }

    @Override
    public Optional<Pedido> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Pedido save(Pedido entity) {
        return null;
    }

    @Override
    public Pedido update(String s, Pedido entity) {
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
