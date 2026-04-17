package backendworkflow.backend.controllers;

import backendworkflow.backend.models.PasoEditadoEvent;
import backendworkflow.backend.models.PasoMovidoEvent;
import backendworkflow.backend.models.UsuarioConectadoEvent;
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
}
