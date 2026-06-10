package backendworkflow.backend.pago.service;

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
public class CoinGateService {

    private final RestTemplate restTemplate;

    @Value("${coingate.api.key}")
    private String apiKey;

    @Value("${coingate.api.url}")
    private String apiUrl;

    @Value("${backend.url}")
    private String backendUrl;

    public CoinGateService() {
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> crearFactura(Double monto, String orderId, String descripcion) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("order_id", orderId);
        requestBody.put("price_amount", monto);
        requestBody.put("price_currency", "USD");
        requestBody.put("receive_currency", "USD");
        requestBody.put("title", "Pago por " + descripcion);
        requestBody.put("description", "Servicio gestionado a través de nuestro portal");
        // Callback al backend (usando URL del entorno)
        requestBody.put("callback_url", backendUrl + "/api/pagos/webhook");
        
        // Return front end URL
        requestBody.put("cancel_url", "https://frontend-workflow.vercel.app/portal/tramites");
        requestBody.put("success_url", "https://frontend-workflow.vercel.app/portal/tramites");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/orders",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con CoinGate: " + e.getMessage(), e);
        }
    }
}
