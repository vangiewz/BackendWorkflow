package backendworkflow.backend.chatbot.controller;

import backendworkflow.backend.chatbot.dto.ChatRequest;
import backendworkflow.backend.chatbot.dto.ChatResponse;
import backendworkflow.backend.chatbot.service.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/enrutar")
    public ResponseEntity<ChatResponse> enrutar(@RequestBody ChatRequest request) {
        if (request.getMensajeUsuario() == null || request.getMensajeUsuario().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ChatResponse(null, "El mensaje no puede estar vacío"));
        }
        
        ChatResponse response = chatbotService.enrutarMensaje(request);
        return ResponseEntity.ok(response);
    }
}
