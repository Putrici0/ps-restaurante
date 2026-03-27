package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Notificacion;
import model.TipoNotificacion;
import repository.interfaces.NotificacionRepository;

import java.util.List;
import java.util.Optional;

public class FirestoreNotificacionRepository implements NotificacionRepository {

    private final Firestore db;

    public FirestoreNotificacionRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public List<Notificacion> findByCuenta(Cuenta cuenta) {
        return List.of();
    }

    @Override
    public List<Notificacion> findByTipoNotificacion(TipoNotificacion tipoNotificacion) {
        return List.of();
    }

    @Override
    public List<Notificacion> findByLeida(boolean leida) {
        return List.of();
    }

    @Override
    public List<Notificacion> findAll() {
        return List.of();
    }

    @Override
    public Optional<Notificacion> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Notificacion save(Notificacion entity) {
        return null;
    }

    @Override
    public Notificacion update(String s, Notificacion entity) {
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
