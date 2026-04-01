package repository.firestore;

import com.google.cloud.firestore.*;
import repository.interfaces.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Base abstract class for all Firestore-based repositories.
 * Implements common CRUD logic to avoid code duplication across different repositories.
 * 
 * @param <T> The entity type (e.g., Plato, Usuario, etc.)
 */
public abstract class AbstractFirestoreRepository<T> implements Repository<T, String> {

    protected final CollectionReference collection;

    /**
     * Constructor that children must call with the Firestore database and the collection name.
     */
    protected AbstractFirestoreRepository(Firestore db, String collectionName) {
        this.collection = db.collection(collectionName);
    }

    // --- Abstract mapping methods (to be implemented by children for Records) ---

    /**
     * Converts a Firestore data map into the corresponding Java Record.
     */
    protected abstract T mapToEntity(String id, Map<String, Object> data);

    /**
     * Converts the Record into a Map for Firestore persistence.
     */
    protected abstract Map<String, Object> entityToMap(T entity);

    /**
     * Extracts the current ID from the Record.
     */
    protected abstract String getEntityId(T entity);

    /**
     * Creates a new instance of the Record with a provided ID (required due to immutability).
     */
    protected abstract T createWithId(T entity, String id);

    // --- Generic CRUD Implementation ---

    @Override
    public Optional<T> findById(String id) {
        try {
            // Firestore works with Futures; we use .get() to block and wait for the result
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
        try {
            return collection.get().get().getDocuments().stream()
                    .map(doc -> mapToEntity(doc.getId(), doc.getData()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error listing collection", e);
        }
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
            
            // .create() fails if the document already exists, ensuring strict creation
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
            // .set() is safe here as we already verified existence
            collection.document(id).set(entityToMap(entityWithId)).get();
            return entityWithId;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error updating entity: " + id, e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            // Added .get() to ensure blocking deletion
            collection.document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error deleting document: " + id, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        try {
            // More efficient check without fetching data
            return collection.document(id).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }
}
