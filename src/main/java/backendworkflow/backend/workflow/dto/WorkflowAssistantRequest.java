package backendworkflow.backend.workflow.dto;

import java.util.Map;

public record WorkflowAssistantRequest(
        String prompt,
        String operatorRole,
        String mode,
        Map<String, Object> workflowDraft
) {
}
