package repository;

import com.google.cloud.firestore.*;

import model.Usuario;

public class UsuarioRepository {

    private static final String COLLECTION = "usuarios";

    private final Firestore db;

    public UsuarioRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Usuario usuario) throws Exception {

        db.collection(COLLECTION)
                .document(usuario.id())
                .set(usuario)
                .get();

    }

    public Usuario findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Usuario.class);

    }
}