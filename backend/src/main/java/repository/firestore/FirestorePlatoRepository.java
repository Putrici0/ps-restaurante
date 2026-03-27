package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Categoria;
import model.Plato;
import repository.interfaces.PlatoRepository;

import java.util.List;
import java.util.Optional;

public class FirestorePlatoRepository implements PlatoRepository {

    private final Firestore db;

    public FirestorePlatoRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public List<Plato> findByCategoria(Categoria categoria) {
        return List.of();
    }

    @Override
    public List<Plato> findByActivo(Boolean activo) {
        return List.of();
    }

    @Override
    public List<Plato> findByNombre(String nombre) {
        return List.of();
    }

    @Override
    public List<Plato> findAll() {
        return List.of();
    }

    @Override
    public Optional<Plato> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Plato save(Plato entity) {
        return null;
    }

    @Override
    public Plato update(String s, Plato entity) {
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
