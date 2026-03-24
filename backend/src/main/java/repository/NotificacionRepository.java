package repository;

import com.google.cloud.firestore.*;

import model.Notificacion;

public class NotificacionRepository {

    private static final String COLLECTION = "notificaciones";

    private final Firestore db;

    public NotificacionRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Notificacion notificacion) throws Exception {

        db.collection(COLLECTION)
                .document(notificacion.id())
                .set(notificacion)
                .get();

    }

    public Notificacion findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Notificacion.class);

    }
}