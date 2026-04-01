package repository.firestore;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import model.Categoria;
import model.Plato;
import repository.interfaces.PlatoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class FirestorePlatoRepository extends AbstractFirestoreRepository<Plato> implements PlatoRepository {

    public FirestorePlatoRepository(Firestore db) {
        super(db, "platos");
    }

    // --- Record Mapping Implementation ---

    @Override
    protected Plato mapToEntity(String id, Map<String, Object> data) {
        // We read from Firestore as Long (cents) and convert to BigDecimal for the app
        Object priceObj = data.get("price");
        BigDecimal price = BigDecimal.ZERO;
        if (priceObj instanceof Number) {
            // Divide cents by 100 to get the original decimal price
            price = BigDecimal.valueOf(((Number) priceObj).longValue(), 2);
        }

        return new Plato(
            id,
            (String) data.get("nombre"),
            Categoria.valueOf((String) data.get("categoria")),
            (String) data.get("descripcion"),
            price,
            (Boolean) data.get("activo")
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Plato plato) {
        // We multiply the BigDecimal by 100 and store it as a Long (cents)
        long priceInCents = plato.price().movePointRight(2).longValue();
        
        return Map.of(
            "nombre", plato.nombre(),
            "categoria", plato.categoria().name(),
            "descripcion", plato.descripcion(),
            "price", priceInCents, 
            "activo", plato.activo()
        );
    }

    @Override
    protected String getEntityId(Plato plato) {
        return plato.id();
    }

    @Override
    protected Plato createWithId(Plato plato, String id) {
        return new Plato(
            id,
            plato.nombre(),
            plato.categoria(),
            plato.descripcion(),
            plato.price(),
            plato.activo()
        );
    }

    // --- PlatoRepository Specific Implementation ---

    @Override
    public List<Plato> findByCategoria(Categoria categoria) {
        try {
            QuerySnapshot query = collection.whereEqualTo("categoria", categoria.name()).get().get();
            return query.getDocuments().stream()
                    .map(doc -> mapToEntity(doc.getId(), doc.getData()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error fetching platos by categoria: " + categoria, e);
        }
    }

    @Override
    public List<Plato> findByActivo(Boolean activo) {
        try {
            QuerySnapshot query = collection.whereEqualTo("activo", activo).get().get();
            return query.getDocuments().stream()
                    .map(doc -> mapToEntity(doc.getId(), doc.getData()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error fetching active/inactive platos", e);
        }
    }

    @Override
    public List<Plato> findByNombre(String nombre) {
        try {
            // Note: Firestore doesn't support "contains" directly for strings without third-party indexing.
            // This is an exact match for name.
            QuerySnapshot query = collection.whereEqualTo("nombre", nombre).get().get();
            return query.getDocuments().stream()
                    .map(doc -> mapToEntity(doc.getId(), doc.getData()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error fetching platos by nombre: " + nombre, e);
        }
    }
}
