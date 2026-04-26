package backendworkflow.backend.services;

import backendworkflow.backend.models.Tramite;
import backendworkflow.backend.models.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class N8nNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(N8nNotificationService.class);
    private final RestTemplate restTemplate;

    @Value("${n8n.webhook.url:}")
    private String n8nWebhookUrl;

    public N8nNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    private void enviarNotificacion(Tramite tramite, Usuario usuario, String promptContexto) {
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) {
            logger.warn("N8N_WEBHOOK_URL no está configurada. Omitiendo notificación.");
            return;
        }

        try {
            String fcmToken = "";
            if (usuario.getFcmTokens() != null && !usuario.getFcmTokens().isEmpty()) {
                for (int i = usuario.getFcmTokens().size() - 1; i >= 0; i--) {
                    String token = usuario.getFcmTokens().get(i);
                    if (token != null && !token.isBlank() && !token.contains("dummy")) {
                        fcmToken = token;
                        break;
                    }
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("nombre", usuario.getNombre());
            payload.put("email", usuario.getEmail());
            payload.put("fcm_token", fcmToken);
            payload.put("tramite_id", tramite.getId());
            payload.put("tramite_nombre", tramite.getNombrePlantilla());
            payload.put("prompt_contexto", promptContexto);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForObject(n8nWebhookUrl, request, String.class);
            logger.info("Notificación enviada a n8n para el trámite {}: {}", tramite.getId(), promptContexto);
        } catch (Exception e) {
            logger.error("Error al enviar notificación a n8n para el trámite {}: {}", tramite.getId(), e.getMessage());
        }
    }

    @Async
    public void notificarTramiteFinalizado(Tramite tramite, Usuario usuario) {
        enviarNotificacion(tramite, usuario, "El trámite ha terminado con éxito. Dile que ya puede recoger sus documentos.");
    }

    @Async
    public void notificarTramiteGratis(Tramite tramite, Usuario usuario) {
        enviarNotificacion(tramite, usuario, "Es un trámite gratuito. Dile que ya está en cola de espera.");
    }

    @Async
    public void notificarTramitePagado(Tramite tramite, Usuario usuario) {
        enviarNotificacion(tramite, usuario, "El pago fue exitoso. Dile que su trámite ya entró a revisión.");
    }
}
