package repository;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

public class FirestoreClientProvider {

    private static Firestore firestore;

    public static Firestore getFirestore() {

        if (firestore == null) {
            firestore = FirestoreClient.getFirestore();
        }

        return firestore;
    }
}