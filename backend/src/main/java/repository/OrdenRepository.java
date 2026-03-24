package repository;

import com.google.cloud.firestore.*;

import model.Orden;

public class OrdenRepository {

    private static final String COLLECTION = "ordenes";

    private final Firestore db;

    public OrdenRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Orden orden) throws Exception {

        db.collection(COLLECTION)
                .document(orden.id())
                .set(orden)
                .get();

    }

    public Orden findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Orden.class);

    }
}