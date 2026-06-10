package backendworkflow.backend.documento.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class ColaboracionSocketController {

    @MessageMapping("/tramite/{tramiteId}/documento/{archivoId}")
    @SendTo("/topic/tramite/{tramiteId}/documento/{archivoId}")
    public Map<String, Object> broadcastDelta(@DestinationVariable String tramiteId, 
                                              @DestinationVariable String archivoId, 
                                              Map<String, Object> payload) {
        // Envia el delta a todos los usuarios suscritos a la sala
        return payload;
    }
}
