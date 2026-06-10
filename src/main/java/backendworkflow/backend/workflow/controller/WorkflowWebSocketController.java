package backendworkflow.backend.workflow.controller;


import backendworkflow.backend.workflow.dto.PasoEditadoEvent;
import backendworkflow.backend.workflow.dto.PasoMovidoEvent;
import backendworkflow.backend.workflow.dto.UsuarioConectadoEvent;
import backendworkflow.backend.workflow.dto.WorkflowSyncEvent;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WorkflowWebSocketController {

    @MessageMapping("/workflow/{workflowId}/join")
    @SendTo("/topic/workflow/{workflowId}/join")
    public UsuarioConectadoEvent joinRoom(@DestinationVariable String workflowId, UsuarioConectadoEvent event) {
        return event;
    }

    @MessageMapping("/workflow/{workflowId}/move")
    @SendTo("/topic/workflow/{workflowId}/move")
    public PasoMovidoEvent movePaso(@DestinationVariable String workflowId, PasoMovidoEvent event) {
        return event;
    }

    @MessageMapping("/workflow/{workflowId}/edit")
    @SendTo("/topic/workflow/{workflowId}/edit")
    public PasoEditadoEvent editPaso(@DestinationVariable String workflowId, PasoEditadoEvent event) {
        return event;
    }

    @MessageMapping("/workflow/{workflowId}/sync")
    @SendTo("/topic/workflow/{workflowId}/sync")
    public WorkflowSyncEvent syncWorkflow(@DestinationVariable String workflowId, WorkflowSyncEvent event) {
        return event;
    }
}
