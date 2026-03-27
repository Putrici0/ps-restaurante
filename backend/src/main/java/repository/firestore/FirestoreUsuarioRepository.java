package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Rol;
import model.Usuario;
import repository.interfaces.UsuarioRepository;

import java.util.List;
import java.util.Optional;

public class FirestoreUsuarioRepository implements UsuarioRepository {

    private final Firestore db;

    public FirestoreUsuarioRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public List<Usuario> findByNombre(String nombre) {
        return List.of();
    }

    @Override
    public List<Usuario> findByRol(Rol rol) {
        return List.of();
    }

    @Override
    public List<Usuario> findAll() {
        return List.of();
    }

    @Override
    public Optional<Usuario> findById(String s) {
        return Optional.empty();
    }

    @Override
    public Usuario save(Usuario entity) {
        return null;
    }

    @Override
    public Usuario update(String s, Usuario entity) {
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
