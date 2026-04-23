package backendworkflow.backend.controllers;

import backendworkflow.backend.models.ClaudePromptRequest;
import backendworkflow.backend.models.WorkflowAssistantRequest;
import backendworkflow.backend.services.ClaudeAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflows/ai")
public class WorkflowAiController {

    private final ClaudeAiService claudeAiService;

    public WorkflowAiController(ClaudeAiService claudeAiService) {
        this.claudeAiService = claudeAiService;
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
                    request.workflowDraft()
            );
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
