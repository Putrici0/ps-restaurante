package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Categoria;
import model.Plato;
import repository.interfaces.PlatoRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestorePlatoRepository extends AbstractFirestoreRepository<Plato> implements PlatoRepository {

    public FirestorePlatoRepository(Firestore db) {
        super(db, "platos");
    }

    @Override
    protected Plato mapToEntity(String id, Map<String, Object> data) {
        return new Plato(
            id,
            get(data, "nombre", ""),
            toEnum(Categoria.class, data.get("categoria"), Categoria.Principal),
            get(data, "descripcion", ""),
            toBigDecimal(data.get("precio")),
            get(data, "estaActivo", true)
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Plato plato) {
        Map<String, Object> map = new HashMap<>();
        map.put("nombre", plato.nombre());
        map.put("categoria", plato.categoria().name());
        map.put("descripcion", plato.descripcion());
        map.put("precio", toCents(plato.precio()));
        map.put("estaActivo", plato.estaActivo());
        return map;
    }

    @Override
    protected String getEntityId(Plato plato) {
        return plato.id();
    }

    @Override
    protected Plato createWithId(Plato plato, String id) {
        return new Plato(id, plato.nombre(), plato.categoria(), plato.descripcion(), plato.precio(), plato.estaActivo());
    }

    @Override
    public List<Plato> findByCategoria(Categoria categoria) {
        return buscarPorCampo("categoria", categoria.name());
    }

    @Override
    public List<Plato> findByEstaActivo(boolean estaActivo) {
        return buscarPorCampo("estaActivo", estaActivo);
    }

    @Override
    public List<Plato> findByNombre(String nombre) {
        return buscarPorCampo("nombre", nombre);
    }
}
