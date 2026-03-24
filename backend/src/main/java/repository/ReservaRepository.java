package repository;

import com.google.cloud.firestore.*;

import model.Reserva;

public class ReservaRepository {

    private static final String COLLECTION = "reservas";

    private final Firestore db;

    public ReservaRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Reserva reserva) throws Exception {

        db.collection(COLLECTION)
                .document(reserva.id())
                .set(reserva)
                .get();

    }

    public Reserva findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Reserva.class);

    }
}