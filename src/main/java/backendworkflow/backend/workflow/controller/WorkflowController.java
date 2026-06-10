package backendworkflow.backend.workflow.controller;


import backendworkflow.backend.workflow.model.PlantillaWorkflow;
import backendworkflow.backend.workflow.repository.PlantillaWorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import backendworkflow.backend.workflow.service.ClaudeAiService;
import backendworkflow.backend.tramite.dto.AsistenteFormularioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final PlantillaWorkflowRepository plantillaWorkflowRepository;
    private final ClaudeAiService claudeAiService;
    private final ObjectMapper objectMapper;

    public WorkflowController(PlantillaWorkflowRepository plantillaWorkflowRepository, ClaudeAiService claudeAiService) {
        this.plantillaWorkflowRepository = plantillaWorkflowRepository;
        this.claudeAiService = claudeAiService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> saveWorkflow(@RequestBody PlantillaWorkflow plantilla) {
        if (plantilla.getId() != null && plantilla.getId().isEmpty()) {
            plantilla.setId(null);
        }
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(plantilla);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<PlantillaWorkflow>> getAllWorkflows() {
        return ResponseEntity.ok(plantillaWorkflowRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<PlantillaWorkflow> getWorkflowById(@PathVariable String id) {
        Optional<PlantillaWorkflow> workflow = plantillaWorkflowRepository.findById(id);
        return workflow.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> updateWorkflow(@PathVariable String id, @RequestBody PlantillaWorkflow plantilla) {
        if (!plantillaWorkflowRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        plantilla.setId(id);
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(plantilla);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> toggleActive(@PathVariable String id) {
        Optional<PlantillaWorkflow> optWorkflow = plantillaWorkflowRepository.findById(id);
        if (optWorkflow.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlantillaWorkflow workflow = optWorkflow.get();
        workflow.setActive(!workflow.isActive());
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(workflow);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/asistir-formulario")
    public ResponseEntity<?> asistirFormularioInicial(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String modo = request.get("modo");
        String mensaje = request.get("mensaje");

        if (mensaje == null || mensaje.isBlank()) {
            return ResponseEntity.badRequest().body("Debes enviar un mensaje para solicitar ayuda de IA.");
        }

        Optional<PlantillaWorkflow> optWorkflow = plantillaWorkflowRepository.findById(id);
        if (optWorkflow.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PlantillaWorkflow workflow = optWorkflow.get();
        Map<String, Object> schema = workflow.getFormularioCliente();

        if (schema == null || schema.isEmpty()) {
            return ResponseEntity.ok(new AsistenteFormularioResponse(id, Map.of(), "Este trámite no tiene formulario inicial para autocompletar."));
        }

        try {
            String schemaJson = objectMapper.writeValueAsString(schema);
            String aiRaw = claudeAiService.sugerirCamposFormulario(schemaJson, mensaje, modo != null ? modo : "chat");
            String aiJson = extractJsonObject(aiRaw);

            Map<String, Object> parsed = objectMapper.readValue(aiJson, new TypeReference<Map<String, Object>>() {});
            Object sugerenciaObj = parsed.get("sugerencia");
            Object observacionObj = parsed.get("observacion");

            Map<String, Object> sugerencia = new HashMap<>();
            if (sugerenciaObj instanceof Map<?, ?> suggestionMap) {
                for (Map.Entry<?, ?> entry : suggestionMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (isSchemaFieldAllowed(schema, key)) {
                        sugerencia.put(key, entry.getValue());
                    }
                }
            }
            String observacion = observacionObj == null ? "" : String.valueOf(observacionObj);
            return ResponseEntity.ok(new AsistenteFormularioResponse(id, sugerencia, observacion));
        } catch (Exception e) {
            return ResponseEntity.ok(new AsistenteFormularioResponse(
                    id,
                    Map.of(),
                    "No se pudo autocompletar con IA en este intento. Puedes completar manualmente o intentar de nuevo."
            ));
        }
    }

    private boolean isSchemaFieldAllowed(Map<String, Object> schema, String fieldKey) {
        Object propertiesObj = schema.get("properties");
        if (!(propertiesObj instanceof Map<?, ?> props)) {
            return false;
        }
        return props.containsKey(fieldKey);
    }

    private String extractJsonObject(String aiRaw) {
        if (aiRaw == null) return "{}";
        int firstBrace = aiRaw.indexOf('{');
        int lastBrace = aiRaw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return aiRaw.substring(firstBrace, lastBrace + 1);
        }
        return aiRaw;
    }
}
