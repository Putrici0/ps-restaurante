package config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

public class FirebaseConfig {

    public static void init() {

        try {

            if (FirebaseApp.getApps().isEmpty()) {

                var serviceAccount =
                        FirebaseConfig.class
                                .getClassLoader()
                                .getResourceAsStream("firebase-key.json");

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(
                                GoogleCredentials.fromStream(serviceAccount)
                        )
                        .build();

                FirebaseApp.initializeApp(options);

                System.out.println("Firebase inicializado correctamente");

            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    public static Firestore getFirestore() {

        init();

        return FirestoreClient.getFirestore();

    }
}