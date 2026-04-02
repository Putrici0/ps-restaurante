package config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.InputStream;

public class FirebaseConfig {

    public static void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = FirebaseConfig.class
                        .getClassLoader()
                        .getResourceAsStream("firebase-key.json");

                if (serviceAccount == null) {
                    throw new IllegalStateException("No se encontró firebase-key.json en el classpath");
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("Firebase inicializado correctamente");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al inicializar Firebase", e);
        }
    }

    public static Firestore getFirestore() {
        init();
        return FirestoreClient.getFirestore();
    }
}