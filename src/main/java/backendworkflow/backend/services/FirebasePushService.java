package backendworkflow.backend.services;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FirebasePushService {

    private static final Logger logger = LoggerFactory.getLogger(FirebasePushService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${firebase.project.id:}")
    private String projectId;

    private GoogleCredentials googleCredentials;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (projectId == null || projectId.isBlank()) {
            logger.warn("FIREBASE_PROJECT_ID no configurado. Push notifications deshabilitadas.");
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
            if (!resource.exists()) {
                logger.warn("firebase-service-account.json no encontrado. Push notifications deshabilitadas.");
                return;
            }
            googleCredentials = GoogleCredentials
                    .fromStream(resource.getInputStream())
                    .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
            initialized = true;
            logger.info("FirebasePushService inicializado correctamente para proyecto: {}", projectId);
        } catch (IOException e) {
            logger.error("Error al inicializar Firebase credentials: {}", e.getMessage());
        }
    }

    /**
     * Versión @Async — llamada desde fuera del bean (Spring proxy funciona).
     * Usada solo cuando se llama directamente desde otro servicio que NO es @Async.
     */
    @Async
    public void enviarPushAUsuario(List<String> fcmTokens, String title, String body) {
        enviarPushAUsuarioSync(fcmTokens, title, body);
    }

    /**
     * Versión SÍNCRONA — para usar dentro de otro método @Async.
     * NO tiene @Async, se ejecuta en el mismo thread.
     */
    public void enviarPushAUsuarioSync(List<String> fcmTokens, String title, String body) {
        if (!initialized || fcmTokens == null || fcmTokens.isEmpty()) return;

        for (String token : fcmTokens) {
            try {
                enviarPushInterno(token, title, body);
            } catch (Exception e) {
                logger.warn("Error push a token {}...: {}",
                        token.substring(0, Math.min(15, token.length())), e.getMessage());
            }
        }
    }

    private void enviarPushInterno(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank() || fcmToken.contains("dummy")) {
            return;
        }

        try {
            googleCredentials.refreshIfExpired();
            String accessToken = googleCredentials.getAccessToken().getTokenValue();

            String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("body", body);

            Map<String, Object> message = new HashMap<>();
            message.put("token", fcmToken);
            message.put("notification", notification);

            Map<String, Object> payload = new HashMap<>();
            payload.put("message", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Push enviado OK a token: {}...", fcmToken.substring(0, Math.min(20, fcmToken.length())));
            } else {
                logger.warn("Push fallido ({}): {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            logger.warn("Error push (token: {}...): {}",
                    fcmToken.substring(0, Math.min(20, fcmToken.length())), e.getMessage());
        }
    }
}
