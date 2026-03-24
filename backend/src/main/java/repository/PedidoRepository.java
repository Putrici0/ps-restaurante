package repository;

import com.google.cloud.firestore.*;

import model.Pedido;

public class PedidoRepository {

    private static final String COLLECTION = "pedidos";

    private final Firestore db;

    public PedidoRepository() {

        this.db = FirestoreClientProvider.getFirestore();

    }

    public void save(Pedido pedido) throws Exception {

        db.collection(COLLECTION)
                .document(pedido.id())
                .set(pedido)
                .get();

    }

    public Pedido findById(String id) throws Exception {

        DocumentSnapshot doc =
                db.collection(COLLECTION)
                        .document(id)
                        .get()
                        .get();

        if (!doc.exists()) return null;

        return doc.toObject(Pedido.class);

    }
}