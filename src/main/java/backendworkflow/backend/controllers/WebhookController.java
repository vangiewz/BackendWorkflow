package backendworkflow.backend.controllers;

import backendworkflow.backend.services.TramiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private final TramiteService tramiteService;

    public WebhookController(TramiteService tramiteService) {
        this.tramiteService = tramiteService;
    }

    /**
     * Health-check para verificar que el endpoint de webhooks es accesible.
     */
    @GetMapping("/coingate")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Webhook endpoint activo");
    }

    /**
     * CoinGate envía el payload como un JSON en el cuerpo (Body) de la petición.
     * Usamos @RequestBody para capturarlo correctamente.
     */
    @PostMapping("/coingate")
    public ResponseEntity<String> handleCoinGateWebhook(@RequestBody Map<String, Object> payload) {
        logger.info("🚨 WEBHOOK ENTRANDO A LA APLICACIÓN 🚨");
        logger.info("➡️ Payload completo recibido: {}", payload);

        try {
            // Extraemos los valores haciendo cast a String
            String status = (String) payload.get("status");
            String orderId = (String) payload.get("order_id");

            logger.info("🔍 Estado extraído: {} | Order ID: {}", status, orderId);

            if ("paid".equalsIgnoreCase(status)) {
                logger.info("✅ Entró al IF de 'paid'. Ejecutando confirmarPago() para el trámite...");
                tramiteService.confirmarPago(orderId);
                logger.info("✅ confirmarPago() finalizado con éxito.");
            } else {
                logger.info("⚠️ El estado no es 'paid', es: {}", status);
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("❌ ERROR al procesar webhook de CoinGate: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}