package backendworkflow.backend.notificacion.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = getClass().getClassLoader()
                        .getResourceAsStream("firebase-service-account.json");

                if (serviceAccount == null) {
                    System.err.println("❌ No se encontró firebase-service-account.json en el classpath");
                    return;
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase inicializado exitosamente");
            }
        } catch (IOException e) {
            System.err.println("❌ Error al inicializar Firebase: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
