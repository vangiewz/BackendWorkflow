package backendworkflow.backend.chatbot.service;

import backendworkflow.backend.chatbot.dto.ChatRequest;
import backendworkflow.backend.chatbot.dto.ChatResponse;
import backendworkflow.backend.workflow.model.PlantillaWorkflow;
import backendworkflow.backend.workflow.repository.PlantillaWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatbotService {

    private final PlantillaWorkflowRepository plantillaRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.microservice.use-localhost}")
    private boolean useLocalhost;

    @Value("${ai.microservice.local-url}")
    private String localUrl;

    @Value("${ai.microservice.prod-url}")
    private String prodUrl;

    public ChatbotService(PlantillaWorkflowRepository plantillaRepository) {
        this.plantillaRepository = plantillaRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private String getMicroserviceBaseUrl() {
        return useLocalhost ? localUrl : prodUrl;
    }

    public ChatResponse enrutarMensaje(ChatRequest request) {
        // 1. Obtener catálogo de trámites activos
        List<PlantillaWorkflow> activas = plantillaRepository.findByIsActiveTrue();
        StringBuilder catalogoStr = new StringBuilder();
        
        for (PlantillaWorkflow p : activas) {
            catalogoStr.append("- ID: ").append(p.getId())
                       .append(" | Nombre: ").append(p.getNombre())
                       .append(" | Descripción: ").append(p.getDescripcion() != null ? p.getDescripcion() : "N/A")
                       .append("\n");
        }

        // 2. Preparar el payload para FastAPI
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("mensajeUsuario", request.getMensajeUsuario());
        requestBody.put("catalogoTramites", catalogoStr.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/chatbot/enrutar";

        try {
            // 3. Hacer petición a FastAPI
            ResponseEntity<ChatResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    ChatResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            // Fallback en caso de error
            return new ChatResponse(null, "Lo siento, el servicio de asistencia no está disponible en este momento. Inténtalo más tarde.");
        }
    }
}
