package backendworkflow.backend.tramite.controller;

import backendworkflow.backend.tramite.dto.DocumentoGestionDTO;
import backendworkflow.backend.tramite.service.DocumentoGestionService;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documentos/gestion")
public class DocumentoGestionController {

    private final DocumentoGestionService documentoGestionService;
    private final UsuarioService usuarioService;

    public DocumentoGestionController(DocumentoGestionService documentoGestionService, UsuarioService usuarioService) {
        this.documentoGestionService = documentoGestionService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<List<DocumentoGestionDTO>> obtenerDocumentos(
            @RequestParam(defaultValue = "PENDIENTES") String tipo,
            Authentication authentication) {
        
        String email = authentication.getName();
        Usuario usuario = usuarioService.findByEmail(email).orElse(null);
        
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<DocumentoGestionDTO> documentos = documentoGestionService.obtenerDocumentos(usuario, tipo);
        return ResponseEntity.ok(documentos);
    }
}
