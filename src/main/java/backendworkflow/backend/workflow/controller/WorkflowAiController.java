package backendworkflow.backend.workflow.controller;

import backendworkflow.backend.workflow.dto.ClaudePromptRequest;
import backendworkflow.backend.workflow.dto.WorkflowAssistantRequest;
import backendworkflow.backend.workflow.service.ClaudeAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/ai")
public class WorkflowAiController {

    private final ClaudeAiService claudeAiService;
    private final backendworkflow.backend.workflow.service.PredictiveRoutingClient predictiveRoutingClient;
    private final backendworkflow.backend.tramite.service.SlaEscalationService slaEscalationService;

    public WorkflowAiController(ClaudeAiService claudeAiService,
            backendworkflow.backend.workflow.service.PredictiveRoutingClient predictiveRoutingClient,
            backendworkflow.backend.tramite.service.SlaEscalationService slaEscalationService) {
        this.claudeAiService = claudeAiService;
        this.predictiveRoutingClient = predictiveRoutingClient;
        this.slaEscalationService = slaEscalationService;
    }

    @GetMapping("/routing/escalated")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEscalatedTramites() {
        return ResponseEntity.ok(slaEscalationService.getEscalatedTramites());
    }

    @GetMapping("/routing/anomalies")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAnomalousTramites() {
        return ResponseEntity.ok(slaEscalationService.getAnomalousTramites());
    }

    @PostMapping("/routing/force-sla-check")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> forceSlaCheck() {
        try {
            java.util.Map<String, Object> resultado = slaEscalationService.evaluateSlaManual();
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/routing/simulate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simulateRouting(@RequestBody java.util.Map<String, Object> request) {
        try {
            String nombrePlantilla = (String) request.getOrDefault("nombrePlantilla", "Trámite de Prueba");
            String descripcionPolitica = (String) request.getOrDefault("descripcionPolitica",
                    "Política de prueba generada por IA");
            String departamentoAsignado = (String) request.getOrDefault("departamentoAsignado", "RRHH");
            long cargaActualDepartamento = ((Number) request.getOrDefault("cargaActualDepartamento", 15)).longValue();
            boolean esViernesOFinSemana = (Boolean) request.getOrDefault("esViernesOFinSemana", false);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> datosCliente = (java.util.Map<String, Object>) request.get("datosCliente");

            java.util.Map<String, Object> prediction = predictiveRoutingClient.predictPriority(
                    "sim_tramite_123",
                    "sim_plantilla_123",
                    nombrePlantilla,
                    descripcionPolitica,
                    departamentoAsignado,
                    cargaActualDepartamento,
                    esViernesOFinSemana,
                    datosCliente);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(prediction);
        } catch (Exception e) {
            java.util.Map<String, String> errMap = new java.util.HashMap<>();
            errMap.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return ResponseEntity.badRequest().body(errMap);
        }
    }

    @PostMapping("/generate")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> generateWorkflow(@RequestBody ClaudePromptRequest request) {
        try {
            String jsonOutput = claudeAiService.generarWorkflow(request.politicaNegocio());
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(jsonOutput);
        } catch (Exception e) {
            java.util.Map<String, String> errMap = new java.util.HashMap<>();
            errMap.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return ResponseEntity.badRequest().body(errMap);
        }
    }

    @PostMapping("/assist")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> assistWorkflow(@RequestBody WorkflowAssistantRequest request) {
        try {
            String jsonOutput = claudeAiService.asistirEditorWorkflow(
                    request.prompt(),
                    request.operatorRole(),
                    request.mode(),
                    request.workflowDraft());
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(jsonOutput);
        } catch (Exception e) {
            java.util.Map<String, String> errMap = new java.util.HashMap<>();
            errMap.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return ResponseEntity.badRequest().body(errMap);
        }
    }
}
