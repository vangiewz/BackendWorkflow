package backendworkflow.backend.controllers;

import backendworkflow.backend.models.ClaudePromptRequest;
import backendworkflow.backend.services.ClaudeAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/ai")
public class WorkflowAiController {

    private final ClaudeAiService claudeAiService;

    public WorkflowAiController(ClaudeAiService claudeAiService) {
        this.claudeAiService = claudeAiService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> generateWorkflow(@RequestBody ClaudePromptRequest request) {
        try {
            String jsonOutput = claudeAiService.generarWorkflow(request.politicaNegocio());
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(jsonOutput);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
