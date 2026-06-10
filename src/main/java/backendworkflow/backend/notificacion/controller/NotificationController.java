package backendworkflow.backend.notificacion.controller;


import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final UsuarioRepository usuarioRepository;

    public NotificationController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> request, Authentication authentication) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token es requerido");
        }

        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Evitar duplicados
        if (!usuario.getFcmTokens().contains(token)) {
            usuario.getFcmTokens().add(token);
            usuarioRepository.save(usuario);
        }

        return ResponseEntity.ok(Map.of("message", "Token FCM registrado exitosamente"));
    }

    @DeleteMapping("/token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeToken(@RequestBody Map<String, String> request, Authentication authentication) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token es requerido");
        }

        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.getFcmTokens().remove(token);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("message", "Token FCM eliminado exitosamente"));
    }
}
