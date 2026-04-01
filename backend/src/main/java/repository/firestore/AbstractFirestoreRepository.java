package repository.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import repository.interfaces.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Base abstract class for all Firestore-based repositories.
 * Implements common CRUD logic and provides conversion utilities to reduce boilerplate.
 * 
 * @param <T> The entity type (e.g., Plato, Usuario, etc.)
 */
public abstract class AbstractFirestoreRepository<T> implements Repository<T, String> {

    protected final CollectionReference collection;

    protected AbstractFirestoreRepository(Firestore db, String collectionName) {
        this.collection = db.collection(collectionName);
    }

    // --- Abstract mapping methods (to be implemented by children for Records) ---

    protected abstract T mapToEntity(String id, Map<String, Object> data);
    protected abstract Map<String, Object> entityToMap(T entity);
    protected abstract String getEntityId(T entity);
    protected abstract T createWithId(T entity, String id);

    // --- Generic CRUD Implementation ---

    @Override
    public Optional<T> findById(String id) {
        try {
            DocumentSnapshot doc = collection.document(id).get().get();
            if (doc.exists()) {
                return Optional.of(mapToEntity(doc.getId(), doc.getData()));
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error reading from Firestore: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<T> findAll() {
        return buscar(collection);
    }

    @Override
    public T save(T entity) {
        try {
            String id = getEntityId(entity);
            DocumentReference docRef;

            if (id == null || id.isEmpty()) {
                docRef = collection.document();
                id = docRef.getId();
            } else {
                docRef = collection.document(id);
            }

            T entityWithId = createWithId(entity, id);
            docRef.create(entityToMap(entityWithId)).get();
            return entityWithId;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Could not save entity. It might already exist or there's a connection issue.", e);
        }
    }

    @Override
    public T update(String id, T entity) {
        try {
            if (!existsById(id)) {
                throw new RuntimeException("Cannot update: Document with ID " + id + " does not exist.");
            }

            T entityWithId = createWithId(entity, id);
            collection.document(id).set(entityToMap(entityWithId)).get();
            return entityWithId;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error updating entity: " + id, e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            collection.document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error deleting document: " + id, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        try {
            return collection.document(id).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    // --- High-Level Search Utilities ---

    /**
     * Executes a query and maps the results to a list of entities.
     */
    protected List<T> buscar(Query query) {
        try {
            QuerySnapshot result = query.get().get();
            return result.getDocuments().stream()
                    .map(doc -> mapToEntity(doc.getId(), doc.getData()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error executing Firestore query", e);
        }
    }

    /**
     * Executes a simple equality search on a specific field.
     */
    protected List<T> buscarPorCampo(String campo, Object valor) {
        return buscar(collection.whereEqualTo(campo, valor));
    }

    // --- Type Conversion Utilities (The "Magic" part) ---

    protected Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        }
        return null;
    }

    protected Timestamp toTimestamp(Instant instant) {
        return (instant != null) ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    protected BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).longValue(), 2);
        }
        return BigDecimal.ZERO;
    }

    protected Long toCents(BigDecimal amount) {
        return (amount != null) ? amount.movePointRight(2).longValue() : 0L;
    }

    protected <E extends Enum<E>> E toEnum(Class<E> enumClass, Object value, E defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(enumClass, value.toString());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // --- Safe Value Extraction ---

    @SuppressWarnings("unchecked")
    protected <V> V get(Map<String, Object> data, String key, V defaultValue) {
        Object val = data.get(key);
        return (val != null) ? (V) val : defaultValue;
    }
}
