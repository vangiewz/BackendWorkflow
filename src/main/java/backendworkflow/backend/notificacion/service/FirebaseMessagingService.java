package backendworkflow.backend.notificacion.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FirebaseMessagingService {

    public void sendNotification(String token, String title, String body) {
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(token)
                .setNotification(notification)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ Mensaje enviado exitosamente a: " + token + " | Respuesta: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("❌ Error al enviar mensaje FCM: " + e.getMessage());
        }
    }

    public void sendNotificationToMultipleTokens(List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            sendNotification(token, title, body);
        }
    }
}
