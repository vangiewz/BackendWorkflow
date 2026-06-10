package backendworkflow.backend.documento.controller;

import backendworkflow.backend.storage.service.S3DocumentoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import backendworkflow.backend.usuario.service.UsuarioService;
import backendworkflow.backend.usuario.model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final S3DocumentoService s3DocumentoService;
    private final UsuarioService usuarioService;

    public DocumentoController(S3DocumentoService s3DocumentoService, UsuarioService usuarioService) {
        this.s3DocumentoService = s3DocumentoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/{archivoId}/descargar")
    public ResponseEntity<InputStreamResource> descargarDocumento(
            @PathVariable String archivoId,
            @RequestHeader(value = "X-Usuario-Solicitante", required = false) String headerUsuario,
            @RequestHeader(value = "X-Departamento-Solicitante", required = false) String headerDepto,
            Authentication authentication
    ) {
        try {
            String usuarioSolicitante = headerUsuario;
            String departamentoSolicitante = headerDepto;
            String rolSolicitante = null;

            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                Usuario usuario = usuarioService.findByEmail(authentication.getName()).orElse(null);
                if (usuario != null) {
                    usuarioSolicitante = usuario.getId();
                    departamentoSolicitante = usuario.getDepartamentoId();
                    rolSolicitante = usuario.getRol();
                }
            }

            InputStream is = s3DocumentoService.descargarDocumento(archivoId, usuarioSolicitante, departamentoSolicitante, rolSolicitante);
            InputStreamResource resource = new InputStreamResource(is);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documento_" + archivoId + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.status(403).build(); // 403 Forbidden o 404 Not Found dependiendo del caso
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
