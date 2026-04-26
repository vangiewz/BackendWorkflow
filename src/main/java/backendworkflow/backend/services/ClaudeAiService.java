package backendworkflow.backend.services;

import backendworkflow.backend.models.Departamento;
import backendworkflow.backend.repositories.DepartamentoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.stream.Collectors;

@Service
public class ClaudeAiService {

    private final DepartamentoRepository departamentoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.microservice.use-localhost}")
    private boolean useLocalhost;

    @Value("${ai.microservice.local-url}")
    private String localUrl;

    @Value("${ai.microservice.prod-url}")
    private String prodUrl;

    public ClaudeAiService(DepartamentoRepository departamentoRepository) {
        this.departamentoRepository = departamentoRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private String getMicroserviceBaseUrl() {
        return useLocalhost ? localUrl : prodUrl;
    }

    public String generarWorkflow(String politicaNegocio) {
        List<Departamento> departamentos = departamentoRepository.findByIsActiveTrue();
        
        List<Map<String, String>> deptosPayload = departamentos.stream()
            .map(d -> {
                Map<String, String> map = new HashMap<>();
                map.put("id", d.getId());
                map.put("nombre", d.getNombre());
                return map;
            })
            .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("politicaNegocio", politicaNegocio != null ? politicaNegocio : "");
        requestBody.put("departamentos", deptosPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/generate";
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con el microservicio AI: " + e.getMessage(), e);
        }
    }

    public String asistirEditorWorkflow(
            String prompt,
            String operatorRole,
            String mode,
            Map<String, Object> workflowDraft
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Debes enviar una consulta para el asistente.");
        }

        List<Departamento> departamentos = departamentoRepository.findByIsActiveTrue();
        List<Map<String, String>> deptosPayload = departamentos.stream()
            .map(d -> {
                Map<String, String> map = new HashMap<>();
                map.put("id", d.getId());
                map.put("nombre", d.getNombre());
                return map;
            })
            .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("operatorRole", operatorRole != null ? operatorRole : "ADMIN");
        requestBody.put("mode", mode != null ? mode : "create");
        requestBody.put("workflowDraft", workflowDraft);
        requestBody.put("departamentos", deptosPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/assist";
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            // Fallback en caso de error de conexión o del microservicio
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("respuesta", "El microservicio de IA no está disponible en este momento. (" + e.getMessage() + ")");
            fallback.put("guiaUso", List.of());
            fallback.put("correccionesDetectadas", List.of());
            fallback.put("workflowSugerido", null);
            return toJsonSafe(fallback);
        }
    }

    public String analizarLogsTramites(String logsCompactosJson, double horasEsperadasPromedio) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("logsCompactosJson", logsCompactosJson);
        requestBody.put("horasEsperadasPromedio", horasEsperadasPromedio);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/analyze-logs";
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("insights", List.of());
            fallback.put("planAccion", List.of());
            return toJsonSafe(fallback);
        }
    }

    public String sugerirCamposFormulario(String schemaJson, String textoUsuario, String modo) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("schemaJson", schemaJson);
        requestBody.put("textoUsuario", textoUsuario);
        requestBody.put("modo", modo);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/suggest-fields";
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("sugerencia", new HashMap<>());
            fallback.put("observacion", "Microservicio no disponible.");
            return toJsonSafe(fallback);
        }
    }

    private String toJsonSafe(Map<String, Object> value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public String resumirTramite(String tramiteCompactoJson) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tramiteCompacto", tramiteCompactoJson);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String endpoint = getMicroserviceBaseUrl() + "/api/workflows/ai/summarize-tramite";

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("titulo", "Resumen no disponible");
            fallback.put("estado", "Completado");
            fallback.put("resumen", "No fue posible generar el resumen en este momento.");
            fallback.put("pasosClave", List.of());
            fallback.put("conclusion", "Su trámite ha sido procesado. Contacte a la institución para más detalles.");
            return toJsonSafe(fallback);
        }
    }
}
