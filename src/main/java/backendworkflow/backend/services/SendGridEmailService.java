package backendworkflow.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SendGridEmailService {

    private static final Logger logger = LoggerFactory.getLogger(SendGridEmailService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sendgrid.api.key:}")
    private String apiKey;

    @Value("${sendgrid.from.email:no-reply@workflowsystem.com}")
    private String fromEmail;

    /**
     * Versión @Async — para llamadas directas desde servicios NO-async.
     */
    @Async
    public void enviarEmail(String toEmail, String toName, String subject, String htmlContent) {
        enviarEmailSync(toEmail, toName, subject, htmlContent);
    }

    /**
     * Versión SÍNCRONA — para usar dentro de otro método @Async.
     * NO tiene @Async, se ejecuta en el mismo thread.
     */
    public void enviarEmailSync(String toEmail, String toName, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("SENDGRID_API_KEY no configurada. Email NO enviado a '{}'.", toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            logger.warn("Email destino inválido: '{}'. Omitiendo.", toEmail);
            return;
        }

        try {
            String url = "https://api.sendgrid.com/v3/mail/send";

            Map<String, Object> from = new HashMap<>();
            from.put("email", fromEmail);
            from.put("name", "Sistema de Trámites");

            Map<String, Object> to = new HashMap<>();
            to.put("email", toEmail);
            if (toName != null && !toName.isBlank()) {
                to.put("name", toName);
            }

            Map<String, Object> personalization = new HashMap<>();
            personalization.put("to", List.of(to));
            personalization.put("subject", subject);

            Map<String, Object> content = new HashMap<>();
            content.put("type", "text/html");
            content.put("value", htmlContent);

            Map<String, Object> payload = new HashMap<>();
            payload.put("personalizations", List.of(personalization));
            payload.put("from", from);
            payload.put("content", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Email enviado OK a: {}", toEmail);
            } else {
                logger.warn("Email respuesta inesperada ({}): {}", response.getStatusCode(), response.getBody());
            }
        } catch (HttpClientErrorException e) {
            // SendGrid devuelve 403 si el remitente no está verificado, 401 si API key es inválida
            logger.error("SendGrid ERROR HTTP {} enviando a '{}': {}", 
                    e.getStatusCode(), toEmail, e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Error al enviar email a '{}': {}", toEmail, e.getMessage());
        }
    }

    /**
     * Genera HTML para la notificación de paso de trámite.
     */
    public String generarHtmlNotificacion(String nombreDestinatario, String nombreTramite, 
                                           String nombrePaso, boolean esCliente) {
        String saludo = (nombreDestinatario != null && !nombreDestinatario.isBlank()) 
                ? "Hola " + nombreDestinatario + "," 
                : "Hola,";

        String mensaje = esCliente
                ? "Necesitamos que complete información para avanzar con su trámite."
                : "Se le ha asignado un nuevo paso para procesar en el sistema.";

        return """
                <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #0f0f14; color: #e2e8f0; border-radius: 16px; overflow: hidden;">
                  <div style="background: linear-gradient(135deg, #7c3aed 0%%, #a855f7 100%%); padding: 32px 24px; text-align: center;">
                    <h1 style="margin: 0; font-size: 22px; color: white;">📋 Sistema de Trámites</h1>
                  </div>
                  <div style="padding: 32px 24px;">
                    <p style="font-size: 16px; margin-bottom: 8px;">%s</p>
                    <p style="color: #9ca3af; font-size: 14px; margin-bottom: 24px;">%s</p>
                    <div style="background: #16161d; border: 1px solid #2a2a38; border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                      <p style="margin: 0 0 12px 0; font-size: 13px; color: #9ca3af; text-transform: uppercase; letter-spacing: 1px;">Trámite</p>
                      <p style="margin: 0 0 16px 0; font-size: 18px; font-weight: bold; color: #c084fc;">%s</p>
                      <p style="margin: 0 0 8px 0; font-size: 13px; color: #9ca3af; text-transform: uppercase; letter-spacing: 1px;">Paso Asignado</p>
                      <p style="margin: 0; font-size: 16px; font-weight: 600; color: #e2e8f0;">%s</p>
                    </div>
                    <p style="color: #6b7280; font-size: 13px; text-align: center; margin-top: 32px;">
                      Ingrese al sistema para procesar este paso.
                    </p>
                  </div>
                </div>
                """.formatted(saludo, mensaje, nombreTramite, nombrePaso);
    }
}
