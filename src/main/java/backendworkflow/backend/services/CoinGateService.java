package backendworkflow.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class CoinGateService {

    private static final Logger logger = LoggerFactory.getLogger(CoinGateService.class);
    private final RestTemplate restTemplate;

    @Value("${coingate.api.token}")
    private String apiToken;

    @Value("${coingate.api.url:https://api-sandbox.coingate.com/v2}")
    private String apiUrl;

    @Value("${backend.url}")
    private String backendUrl;

    public CoinGateService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Crea una factura en CoinGate Sandbox para pagar con USDT.
     * Retorna un Map con "payment_url" y "id" (paymentId).
     */
    public Map<String, Object> crearFactura(Double monto, String orderId, String orderDescription) {
        String endpoint = apiUrl + "/orders";

        Map<String, Object> payload = new HashMap<>();
        payload.put("price_amount", monto);
        payload.put("price_currency", "USD");
        payload.put("receive_currency", "USDT");
        payload.put("order_id", orderId);
        payload.put("title", orderDescription);
        payload.put("callback_url", backendUrl + "/api/webhooks/coingate");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Token " + apiToken);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
            return response;
        } catch (Exception e) {
            logger.error("Error al crear factura en CoinGate para orderId {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Error al comunicarse con la pasarela de pagos.", e);
        }
    }
}
