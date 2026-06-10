package backendworkflow.backend.pago.controller;


import backendworkflow.backend.tramite.service.TramiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger logger = LoggerFactory.getLogger(PagoController.class);

    private final TramiteService tramiteService;

    public PagoController(TramiteService tramiteService) {
        this.tramiteService = tramiteService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> coinGateWebhook(@RequestBody Map<String, Object> payload) {
        logger.info("Recibido webhook de CoinGate: {}", payload);

        try {
            String status = (String) payload.get("status");
            String orderId = (String) payload.get("order_id");

            if ("paid".equalsIgnoreCase(status)) {
                logger.info("El pago fue confirmado para la orden {}", orderId);
                tramiteService.confirmarPago(orderId);
            } else if ("canceled".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) {
                logger.info("El pago fue cancelado/expirado para la orden {}. Eliminando trámite.", orderId);
                tramiteService.deleteTramite(orderId);
            } else {
                logger.info("Estado de pago ignorado: {} para la orden {}", status, orderId);
            }

            return ResponseEntity.ok("Webhook procesado");
        } catch (Exception e) {
            logger.error("Error al procesar el webhook: ", e);
            return ResponseEntity.badRequest().body("Error al procesar el webhook");
        }
    }
}
