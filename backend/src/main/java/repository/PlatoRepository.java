package repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import java.util.HashMap;
import java.util.Map;

import model.Categoria;
import model.Plato;

import java.util.ArrayList;
import java.util.List;

public class PlatoRepository {

    private static final String COLLECTION = "platos";

    private final Firestore db;

    public PlatoRepository() {
        this.db = FirestoreClientProvider.getFirestore();
    }



    public void save(Plato plato) throws Exception {

        Map<String, Object> data = new HashMap<>();

        data.put("id", plato.id());
        data.put("nombre", plato.nombre());
        data.put("categoria", plato.categoria().name());
        data.put("descripcion", plato.descripcion());
        data.put("price", plato.price());
        data.put("activo", plato.activo());

        db.collection(COLLECTION)
                .document(plato.id())
                .set(data)
                .get();
    }

    public Plato findById(String id) throws Exception {

        DocumentSnapshot doc = db.collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!doc.exists()) return null;

        return new Plato(
                doc.getString("id"),
                doc.getString("nombre"),
                Categoria.valueOf(doc.getString("categoria")),
                doc.getString("descripcion"),
                doc.getDouble("price"),
                doc.getBoolean("activo")
        );
    }

    public List<Plato> findAll() throws Exception {

        ApiFuture<QuerySnapshot> future =
                db.collection(COLLECTION).get();

        List<QueryDocumentSnapshot> docs = future.get().getDocuments();

        List<Plato> platos = new ArrayList<>();

        for (DocumentSnapshot doc : docs) {

            platos.add(doc.toObject(Plato.class));

        }

        return platos;
    }

    public void delete(String id) throws Exception {

        db.collection(COLLECTION)
                .document(id)
                .delete()
                .get();
    }
}