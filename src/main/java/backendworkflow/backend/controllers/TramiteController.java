package backendworkflow.backend.controllers;

import backendworkflow.backend.models.Tramite;
import backendworkflow.backend.models.Usuario;
import backendworkflow.backend.services.TramiteService;
import backendworkflow.backend.services.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

    private final TramiteService tramiteService;
    private final UsuarioService usuarioService;

    public TramiteController(TramiteService tramiteService, UsuarioService usuarioService) {
        this.tramiteService = tramiteService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<List<Tramite>> getAll() {
        return ResponseEntity.ok(tramiteService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<Tramite> getById(@PathVariable String id) {
        return tramiteService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Iniciar un nuevo trámite a partir de una plantilla.
     * Body: { "plantillaId": "...", "clienteId": "...", "datosCliente": {...} }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> iniciar(@RequestBody Map<String, Object> body) {
        try {
            String plantillaId = (String) body.get("plantillaId");
            String clienteId = (String) body.get("clienteId");
            @SuppressWarnings("unchecked")
            Map<String, Object> datosCliente = (Map<String, Object>) body.get("datosCliente");

            Tramite tramite = tramiteService.iniciarTramite(plantillaId, clienteId, datosCliente);
            return ResponseEntity.status(HttpStatus.CREATED).body(tramite);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cliente")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<List<Tramite>> getMisTramites(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(tramiteService.getByClienteId(usuario.getId()));
    }

    /**
     * Responder el formulario del paso actual.
     * Body: { "pasoId": "...", "respuesta": {...}, "decisionElegida": "Aprobado" (solo para DECISION) }
     */
    @PostMapping("/{id}/responder")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> responder(@PathVariable String id, @RequestBody Map<String, Object> body,
                                        Authentication authentication) {
        try {
            String pasoId = (String) body.get("pasoId");
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = (Map<String, Object>) body.get("respuesta");
            String decisionElegida = (String) body.get("decisionElegida");

            // Obtener usuario actual desde JWT (el subject del token es el email)
            String email = authentication.getName();
            Usuario usuario = usuarioService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            // Todos los usuarios (ADMIN y FUNCIONARIO) deben pertenecer al departamento del paso.
            String deptoId = usuario.getDepartamentoId();

            Tramite tramite = tramiteService.responderPaso(
                    id, pasoId, usuario.getId(), usuario.getNombre(),
                    deptoId, respuesta, decisionElegida
            );

            return ResponseEntity.ok(tramite);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
