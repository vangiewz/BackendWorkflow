package backendworkflow.backend.notificacion.service;


import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.usuario.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class N8nNotificationService {

    private final RestTemplate restTemplate;

    @Value("${n8n.webhook.url}")
    private String n8nWebhookUrl;

    public N8nNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Notifica a n8n cuando se paga un trámite (Email Cliente).
     */
    public void notificarTramitePagado(Tramite tramite, Usuario cliente) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("evento", "TRAMITE_PAGADO");
        payload.put("tramiteId", tramite.getId());
        payload.put("nombrePlantilla", tramite.getNombrePlantilla());
        payload.put("clienteEmail", cliente.getEmail());
        payload.put("clienteNombre", cliente.getNombre());
        payload.put("fecha", tramite.getFechaCreacion());
        payload.put("costo", tramite.getPaymentId() != null ? "Pagado" : "Gratis");

        enviarWebhook(payload);
    }

    /**
     * Notifica a n8n cuando se finaliza un trámite (Email Cliente).
     */
    public void notificarTramiteFinalizado(Tramite tramite, Usuario cliente) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("evento", "TRAMITE_FINALIZADO");
        payload.put("tramiteId", tramite.getId());
        payload.put("nombrePlantilla", tramite.getNombrePlantilla());
        payload.put("clienteEmail", cliente.getEmail());
        payload.put("clienteNombre", cliente.getNombre());
        payload.put("fechaFinalizacion", tramite.getFechaFinalizacion());

        enviarWebhook(payload);
    }

    /**
     * Notifica a n8n cuando se inicia un trámite gratuito (Email Cliente).
     */
    public void notificarTramiteGratis(Tramite tramite, Usuario cliente) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("evento", "TRAMITE_GRATIS_INICIADO");
        payload.put("tramiteId", tramite.getId());
        payload.put("nombrePlantilla", tramite.getNombrePlantilla());
        payload.put("clienteEmail", cliente.getEmail());
        payload.put("clienteNombre", cliente.getNombre());
        payload.put("fecha", tramite.getFechaCreacion());

        enviarWebhook(payload);
    }

    private void enviarWebhook(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    n8nWebhookUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("✅ Webhook n8n enviado con éxito. Evento: " + payload.get("evento"));
        } catch (Exception e) {
            System.err.println("❌ Error al enviar webhook a n8n: " + e.getMessage());
        }
    }
}
