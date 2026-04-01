package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Rol;
import model.Usuario;
import repository.interfaces.UsuarioRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreUsuarioRepository extends AbstractFirestoreRepository<Usuario> implements UsuarioRepository {

    public FirestoreUsuarioRepository(Firestore db) {
        super(db, "usuarios");
    }

    @Override
    protected Usuario mapToEntity(String id, Map<String, Object> data) {
        return new Usuario(
            id,
            get(data, "username", ""),
            get(data, "passwordHash", ""),
            toEnum(Rol.class, data.get("rol"), Rol.Camarero),
            toInstant(data.get("fechaCreacion"))
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Usuario usuario) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", usuario.username());
        map.put("passwordHash", usuario.passwordHash());
        map.put("rol", usuario.rol().name());
        map.put("fechaCreacion", toTimestamp(usuario.fechaCreacion()));
        return map;
    }

    @Override
    protected String getEntityId(Usuario usuario) {
        return usuario.id();
    }

    @Override
    protected Usuario createWithId(Usuario usuario, String id) {
        return new Usuario(id, usuario.username(), usuario.passwordHash(), usuario.rol(), usuario.fechaCreacion());
    }

    @Override
    public List<Usuario> findByNombre(String nombre) {
        return buscarPorCampo("username", nombre);
    }

    @Override
    public List<Usuario> findByRol(Rol rol) {
        return buscarPorCampo("rol", rol.name());
    }
}
