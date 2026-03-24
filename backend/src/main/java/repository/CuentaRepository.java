package repository;

import com.google.cloud.firestore.*;

import model.Cuenta;

public class CuentaRepository {

    private static final String COLLECTION = "cuentas";

    private final Firestore db;

    public CuentaRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Cuenta cuenta) throws Exception {

        db.collection(COLLECTION)
                .document(cuenta.id())
                .set(cuenta)
                .get();

    }

    public Cuenta findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Cuenta.class);

    }
}