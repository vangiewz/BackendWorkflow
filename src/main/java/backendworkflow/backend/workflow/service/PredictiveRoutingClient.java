package backendworkflow.backend.workflow.service;

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
public class PredictiveRoutingClient {

    private final RestTemplate restTemplate;

    @Value("${ai.microservice.use-localhost}")
    private boolean useLocalhost;

    @Value("${ai.microservice.local-url}")
    private String localUrl;

    @Value("${ai.microservice.prod-url}")
    private String prodUrl;

    public PredictiveRoutingClient() {
        this.restTemplate = new RestTemplate();
    }

    private String getMicroserviceBaseUrl() {
        return useLocalhost ? localUrl : prodUrl;
    }

    public Map<String, Object> predictPriority(
            String tramiteId, 
            String plantillaId, 
            String nombrePlantilla, 
            String descripcionPolitica,
            String departamentoAsignado, 
            long cargaActualDepartamento,
            boolean esViernesOFinSemana,
            Map<String, Object> datosCliente) {
            
        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/routing/predict-priority";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tramiteId", tramiteId);
        requestBody.put("plantillaId", plantillaId);
        requestBody.put("nombrePlantilla", nombrePlantilla);
        requestBody.put("descripcionPolitica", descripcionPolitica);
        requestBody.put("departamentoAsignado", departamentoAsignado);
        requestBody.put("cargaActualDepartamento", cargaActualDepartamento);
        requestBody.put("esViernesOFinSemana", esViernesOFinSemana);
        requestBody.put("datosCliente", datosCliente != null ? datosCliente : new HashMap<>());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Error calling predictive routing API: " + e.getMessage());
        }

        // Fallback en caso de error
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("prioridad", "MEDIA");
        fallback.put("riesgoDemora", false);
        return fallback;
    }

    public void sendFeedback(Map<String, Object> realData) {
        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/routing/feedback";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(realData, headers);

        try {
            restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
        } catch (Exception e) {
            System.err.println("Error enviando feedback de IA: " + e.getMessage());
        }
    }
}
