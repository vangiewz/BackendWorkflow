package backendworkflow.backend.services;

import backendworkflow.backend.models.Departamento;
import backendworkflow.backend.repositories.DepartamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClaudeAiService {

  private static final int WORKFLOW_MIN_OUTPUT_TOKENS = 2500;
  private static final int WORKFLOW_MAX_OUTPUT_TOKENS = 4000;
  private static final int WORKFLOW_REPAIR_MIN_TOKENS = 2000;
  private static final int WORKFLOW_REPAIR_MAX_TOKENS = 3500;

    private final DepartamentoRepository departamentoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${claude.api.key}")
    private String claudeApiKey;

    public ClaudeAiService(DepartamentoRepository departamentoRepository) {
        this.departamentoRepository = departamentoRepository;
        this.restTemplate = new RestTemplate();
      this.objectMapper = new ObjectMapper();
    }

    public String generarWorkflow(String politicaNegocio) {
        List<Departamento> departamentos = departamentoRepository.findAll();
        
        String stringDeDepartamentosBD = departamentos.stream()
                .map(d -> d.getNombre() + " (ID: " + d.getId() + ")")
                .collect(Collectors.joining(", "));

        String systemPrompt = """
                SOLO JSON sin markdown. Estructura:{"nombreTramite":"","descripcionTramite":"","categoria":"INTERNO|EXTERNO","costoBase":0,"formularioCliente":{"type":"object","properties":{},"required":[]},"pasos":[{"id":"paso_N","tipo":"ACTIVIDAD|DECISION","departamentoId":"id|null","nombrePaso":"","formularioJson":{}|null,"siguientes":{}}]}
                Deptos:[{DEPARTAMENTOS}]. 
                REGLAS: Cliente=departamentoId null. DECISION=formularioJson null, siguientes por condicion (ej. "Aprobado":"paso_3", "Rechazado":"paso_4"). ACTIVIDAD=formularioJson requerido, siguientes:"default" si lineal.
                IMPORTANTE: Evita los bucles infinitos. Si un trámite se rechaza, es preferible que la ruta termine en un paso final de notificación en lugar de volver eternamente al paso 1, al menos hasta que el cliente decida.
                INTERNO=>costoBase=0. Sin "description" en formularios. Sé compacto.
                """.replace("{DEPARTAMENTOS}", stringDeDepartamentosBD);

        int workflowBudget = estimateWorkflowOutputTokens(politicaNegocio);
        String rawResponse = sendToClaude(systemPrompt, politicaNegocio, workflowBudget);

        String normalized = normalizeJsonObject(rawResponse);
        if (normalized != null && isValidWorkflowPayload(normalized)) {
          return normalized;
        }

        int repairBudget = Math.max(WORKFLOW_REPAIR_MIN_TOKENS, Math.min(WORKFLOW_REPAIR_MAX_TOKENS, workflowBudget - 200));
        String repaired = intentarRepararWorkflowJson(rawResponse, stringDeDepartamentosBD, repairBudget);
        String repairedNormalized = normalizeJsonObject(repaired);
        if (repairedNormalized != null && isValidWorkflowPayload(repairedNormalized)) {
          return repairedNormalized;
        }

        return buildFallbackWorkflowJson(politicaNegocio, departamentos);
          }

          public String analizarLogsTramites(String logsCompactosJson, double horasEsperadasPromedio) {
        String systemPrompt = """
          SOLO JSON. Analiza logs de tiempos de tramites. Identifica deptos que superen promedio, sugiere causa(FALTA_PERSONAL|COMPLEJIDAD_FORMULARIO|MIXTO), plan de accion.
          departamentoId="CLIENTE"=tiempo externo, no retraso interno. Severidad: CRITICO>=24h, ADVERTENCIA>=8h<24h, INFO=resto.
          Schema:{"insights":[{"severidad":"","titulo":"","descripcion":"","departamentoId":null,"funcionarioId":null,"retrasoHoras":0,"causaProbable":""}],"planAccion":[{"prioridad":"ALTA|MEDIA|BAJA","accion":"","objetivo":"","plazoHoras":""}]}
          Usa horasEsperadasPromedio como referencia. Sin markdown.
          """;

        String userPrompt = "horasEsperadasPromedio=" + horasEsperadasPromedio + "\n" + logsCompactosJson;
        int budget = estimateTokensByInputSize(userPrompt, 900, 1700, 300);
        return sendToClaude(systemPrompt, userPrompt, budget);
    }

    public String sugerirCamposFormulario(String schemaJson, String textoUsuario, String modo) {
        String systemPrompt = """
          SOLO JSON. Autocompleta formulario desde texto del usuario.
          Formato:{"sugerencia":{"campo":valor},"observacion":"texto corto"}
          Solo campos existentes en properties del schema. Omite si faltan datos. Respeta tipos(string,number,boolean,date). Enum=solo valores permitidos. Sin datos utiles=sugerencia vacia. Sin markdown.
          """;

        String userPrompt = "modo=" + modo + "\nSCHEMA:\n" + schemaJson + "\n\nTEXTO_USUARIO:\n" + textoUsuario;
        int budget = estimateTokensByInputSize(userPrompt, 500, 1000, 150);
        return sendToClaude(systemPrompt, userPrompt, budget);
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

        List<Departamento> departamentos = departamentoRepository.findAll();
        String departamentosDisponibles = departamentos.stream()
                .map(d -> d.getNombre() + " (ID: " + d.getId() + ")")
                .collect(Collectors.joining(", "));

        String workflowDraftJson = toJsonSafe(workflowDraft);

        String systemPrompt = """
                SOLO JSON. Asiste operador en editor de workflows. Detecta inconsistencias, propone correcciones.
                Deptos permitidos:[{DEPARTAMENTOS}]. 
                REGLAS ESTRICTAS (NO MARQUES COMO ERROR LO SIGUIENTE):
                - departamentoId nulo = Paso del CLIENTE. Esto es VÁLIDO Y CORRECTO.
                - tipo DECISION = formularioJson debe ser null. Las decisiones NO llevan formulario. Esto es CORRECTO.
                - tipo ACTIVIDAD = formularioJson requerido, siguientes="default" si es lineal.
                - INTERNO => costoBase=0. No inventar IDs.
                Formato:{"respuesta":"texto breve","guiaUso":[],"correccionesDetectadas":[{"severidad":"ALTA|MEDIA|BAJA","titulo":"","detalle":"","accion":""}],"workflowSugerido":{"nombreTramite":"","descripcionTramite":"","categoria":"","costoBase":0,"formularioCliente":{},"pasos":[]}|null}
                Sin cambios estructurales=workflowSugerido null. Sin markdown.
                """.replace("{DEPARTAMENTOS}", departamentosDisponibles);

        String userPrompt = "role:%s mode:%s\nWORKFLOW:%s\nCONSULTA:%s".formatted(
                operatorRole != null ? operatorRole : "ADMIN",
                mode != null ? mode : "create",
                workflowDraftJson,
                prompt
        );

        int budget = estimateTokensByInputSize(userPrompt, 900, 1800, 250);
        String rawResponse = sendToClaude(systemPrompt, userPrompt, budget);
        String normalized = normalizeJsonObject(rawResponse);
        if (normalized != null) {
            return normalized;
        }
        
        // Si Claude falló en entregar JSON puro y arrojó texto libre, lo estructuramos a la fuerza:
        java.util.Map<String, Object> fallback = new java.util.HashMap<>();
        fallback.put("respuesta", rawResponse.length() > 500 ? rawResponse.substring(0, 500) + "..." : rawResponse);
        fallback.put("guiaUso", java.util.List.of());
        fallback.put("correccionesDetectadas", java.util.List.of());
        fallback.put("workflowSugerido", null);
        return toJsonSafe(fallback);
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

        private String intentarRepararWorkflowJson(String rawResponse, String departamentosDisponibles, int maxTokens) {
            String systemPrompt = """
              SOLO JSON valido compacto. Repara workflow truncado/malformado. Campos requeridos:nombreTramite,descripcionTramite,categoria,costoBase,formularioCliente,pasos[]. Deptos:[{DEPARTAMENTOS}]. Completa coherente si truncado. Sin description ni markdown.
              """.replace("{DEPARTAMENTOS}", departamentosDisponibles);

            String userPrompt = "REPARAR:\n" + rawResponse;
            return sendToClaude(systemPrompt, userPrompt, maxTokens);
        }

        private int estimateWorkflowOutputTokens(String politicaNegocio) {
          return estimateTokensByInputSize(
              politicaNegocio,
              WORKFLOW_MIN_OUTPUT_TOKENS,
              WORKFLOW_MAX_OUTPUT_TOKENS,
              520
          );
        }

        private int estimateTokensByInputSize(String input, int minTokens, int maxTokens, int overhead) {
          int length = input == null ? 0 : input.length();
          int estimatedInputTokens = (length / 4) + overhead;
          // Use 1:1 ratio instead of 1.5x to save tokens — prompts are now compact enough
          int estimatedOutput = estimatedInputTokens;

          if (estimatedOutput < minTokens) {
            return minTokens;
          }
          if (estimatedOutput > maxTokens) {
            return maxTokens;
          }
          return estimatedOutput;
        }

              private String buildFallbackWorkflowJson(String politicaNegocio, List<Departamento> departamentos) {
            String deptoId = departamentos.isEmpty() ? null : departamentos.get(0).getId();

            Map<String, Object> formularioCliente = Map.of(
              "type", "object",
              "properties", Map.of(
                "detalleSolicitud", Map.of("type", "string"),
                "fechaSolicitud", Map.of("type", "string", "format", "date")
              ),
              "required", List.of("detalleSolicitud", "fechaSolicitud")
            );

            java.util.Map<String, Object> paso1 = new java.util.HashMap<>();
            paso1.put("id", "paso_1");
            paso1.put("tipo", "ACTIVIDAD");
            paso1.put("departamentoId", deptoId);
            paso1.put("nombrePaso", "Revision Inicial");
            paso1.put("formularioJson", Map.of("type", "object", "properties", Map.of("datosCompletos", Map.of("type", "boolean")), "required", List.of("datosCompletos")));
            paso1.put("siguientes", Map.of("default", "paso_2"));

            java.util.Map<String, Object> paso2 = new java.util.HashMap<>();
            paso2.put("id", "paso_2");
            paso2.put("tipo", "DECISION");
            paso2.put("departamentoId", deptoId);
            paso2.put("nombrePaso", "Datos Correctos");
            paso2.put("formularioJson", null);
            paso2.put("siguientes", Map.of("Aprobado", "paso_3", "Rechazado", "paso_4"));

            java.util.Map<String, Object> paso3 = new java.util.HashMap<>();
            paso3.put("id", "paso_3");
            paso3.put("tipo", "ACTIVIDAD");
            paso3.put("departamentoId", deptoId);
            paso3.put("nombrePaso", "Resolucion Final");
            paso3.put("formularioJson", Map.of("type", "object", "properties", Map.of("resultado", Map.of("type", "string")), "required", List.of("resultado")));
            paso3.put("siguientes", Map.of());

            java.util.Map<String, Object> paso4 = new java.util.HashMap<>();
            paso4.put("id", "paso_4");
            paso4.put("tipo", "ACTIVIDAD");
            paso4.put("departamentoId", null);
            paso4.put("nombrePaso", "Solicitud de Ajustes");
            paso4.put("formularioJson", Map.of("type", "object", "properties", Map.of("observacionCliente", Map.of("type", "string")), "required", List.of("observacionCliente")));
            paso4.put("siguientes", Map.of("default", "paso_1"));

            String nombre = "Workflow Generado";
            if (politicaNegocio != null && !politicaNegocio.isBlank()) {
                nombre = politicaNegocio.length() > 50 ? politicaNegocio.substring(0, 50).trim() : politicaNegocio.trim();
            }

            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("nombreTramite", nombre);
            payload.put("descripcionTramite", "Flujo generado por fallback automatico.");
            payload.put("categoria", "EXTERNO");
            payload.put("costoBase", 0);
            payload.put("formularioCliente", formularioCliente);
            payload.put("pasos", List.of(paso1, paso2, paso3, paso4));

            try {
                return objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("No se pudo construir el workflow fallback.");
            }
              }

        private boolean isValidWorkflowPayload(String payload) {
          try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isObject()) {
              return false;
            }
            if (!node.hasNonNull("nombreTramite") || !node.hasNonNull("descripcionTramite")) {
              return false;
            }
            if (!node.hasNonNull("categoria") || !node.has("costoBase")) {
              return false;
            }
            if (!node.hasNonNull("formularioCliente")) {
              return false;
            }
            JsonNode pasos = node.get("pasos");
            return pasos != null && pasos.isArray();
          } catch (Exception e) {
            return false;
          }
        }

        private String normalizeJsonObject(String raw) {
          if (raw == null || raw.isBlank()) {
            return null;
          }

          String trimmed = raw.trim();
          if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
            trimmed = trimmed.trim();
          }

          int start = trimmed.indexOf('{');
          if (start < 0) {
            return null;
          }

          int depth = 0;
          boolean inString = false;
          boolean escaped = false;

          for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escaped) {
              escaped = false;
              continue;
            }

            if (c == '\\') {
              escaped = true;
              continue;
            }

            if (c == '"') {
              inString = !inString;
              continue;
            }

            if (inString) {
              continue;
            }

            if (c == '{') {
              depth++;
            } else if (c == '}') {
              depth--;
              if (depth == 0) {
                return trimmed.substring(start, i + 1);
              }
            }
          }

          return null;
        }

    private String sendToClaude(String systemPrompt, String userContent, int maxTokens) {
        Map<String, Object> requestBody = Map.of(
          "model", "claude-haiku-4-5-20251001",
          "max_tokens", maxTokens,
          "system", systemPrompt,
          "messages", List.of(
            Map.of("role", "user", "content", userContent)
          )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", claudeApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.set("content-type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.anthropic.com/v1/messages",
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getBody() != null && response.getBody().containsKey("content")) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getBody().get("content");
            if (!contentList.isEmpty()) {
                return (String) contentList.get(0).get("text");
            }
        }

        throw new RuntimeException("Error al comunicarse con Claude AI");
    }
}
