package backendworkflow.backend.documento.controller;

import backendworkflow.backend.auditoria.service.AuditoriaService;
import backendworkflow.backend.storage.service.S3DocumentoService;
import backendworkflow.backend.tramite.model.ArchivoMetadata;
import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.tramite.repository.TramiteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoColaborativoController {

    private final TramiteRepository tramiteRepository;
    private final S3DocumentoService s3DocumentoService;
    private final AuditoriaService auditoriaService;

    public DocumentoColaborativoController(TramiteRepository tramiteRepository, 
                                           S3DocumentoService s3DocumentoService, 
                                           AuditoriaService auditoriaService) {
        this.tramiteRepository = tramiteRepository;
        this.s3DocumentoService = s3DocumentoService;
        this.auditoriaService = auditoriaService;
    }

    @PostMapping("/{archivoId}/finalizar-edicion")
    public ResponseEntity<?> finalizarEdicion(
            @PathVariable String archivoId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Usuario-Solicitante", required = false) String headerUsuario,
            @RequestHeader(value = "X-Departamento-Solicitante", required = false) String headerDepto,
            org.springframework.security.core.Authentication authentication
    ) {
        String usuarioSolicitante = headerUsuario;
        String departamentoSolicitante = headerDepto;
        
        // Resolver usuario real a través de Spring Security JWT
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            usuarioSolicitante = authentication.getName(); // El email
        }
        
        String contenidoFinal = body.get("contenido");
        if (contenidoFinal == null) {
            return ResponseEntity.badRequest().body("Falta el campo 'contenido'");
        }

        Optional<Tramite> tramiteOpt = tramiteRepository.findByDocumentosArchivoId(archivoId);
        if (tramiteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Tramite tramite = tramiteOpt.get();
        ArchivoMetadata oldMetadata = tramite.getDocumentos().stream()
                .filter(doc -> doc.getArchivoId().equals(archivoId))
                .findFirst()
                .orElse(null);

        if (oldMetadata == null) {
            return ResponseEntity.notFound().build();
        }

        boolean tienePermisoEdicion = false;
        
        if (usuarioSolicitante != null && (usuarioSolicitante.equals(tramite.getClienteId()) || usuarioSolicitante.equals(tramite.getClienteEmail()))) {
            String permisoCliente = oldMetadata.getPermisos().get("CLIENTE");
            if ("EDICION".equals(permisoCliente) || "AMBOS".equals(permisoCliente)) {
                tienePermisoEdicion = true;
            }
        }
        
        if (departamentoSolicitante != null) {
            String permisoDepto = oldMetadata.getPermisos().get(departamentoSolicitante);
            if ("EDICION".equals(permisoDepto) || "AMBOS".equals(permisoDepto)) {
                tienePermisoEdicion = true;
            }
        }

        if (!tienePermisoEdicion) {
            throw new RuntimeException("Acceso Denegado: No tienes permisos de EDICION para finalizar este documento.");
        }

        try {
            byte[] bytes = contenidoFinal.getBytes(StandardCharsets.UTF_8);
            
            // Garantizar que el archivo tenga extensión en S3 para evitar problemas de compatibilidad y Content-Type
            String nombreSaneado = oldMetadata.getNombreOriginal();
            if (nombreSaneado != null && !nombreSaneado.contains(".")) {
                if ("WORD".equals(oldMetadata.getFormato())) {
                    nombreSaneado += ".doc";
                } else if ("EXCEL".equals(oldMetadata.getFormato())) {
                    nombreSaneado += ".xlsx";
                }
                
                // FIX CRÍTICO: Actualizar el nombreOriginal en memoria para que el algoritmo de versionamiento de S3DocumentoService logre hacer match
                oldMetadata.setNombreOriginal(nombreSaneado);
            }

            ArchivoMetadata newMetadata = s3DocumentoService.uploadVirtualFile(
                    bytes, 
                    nombreSaneado, 
                    tramite.getClienteId(), 
                    tramite.getPlantillaId(), 
                    tramite.getId(), 
                    usuarioSolicitante != null ? usuarioSolicitante : "SISTEMA", 
                    departamentoSolicitante != null ? departamentoSolicitante : "SISTEMA", 
                    oldMetadata.getPermisos(), 
                    tramite, 
                    true,
                    oldMetadata.getCampoKey()
            );

            // CLAVE: Heredar el campoKey para que el versionamiento lo encuentre
            newMetadata.setCampoKey(oldMetadata.getCampoKey());
            newMetadata.setFormato(oldMetadata.getFormato()); // Por si acaso también aseguramos el formato

            tramite.getDocumentos().add(newMetadata);
            tramiteRepository.save(tramite);

            auditoriaService.registrarEvento(
                    tramite.getClienteId(),
                    AuditoriaService.DOCUMENTO_EDITADO_COLABORATIVO,
                    usuarioSolicitante != null ? usuarioSolicitante : "SISTEMA",
                    departamentoSolicitante != null ? departamentoSolicitante : "SISTEMA",
                    tramite.getId(),
                    newMetadata.getArchivoId(),
                    "Edición finalizada y guardada como versión " + newMetadata.getVersion() + " del documento " + oldMetadata.getNombreOriginal()
            );

            return ResponseEntity.ok(newMetadata);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al exportar documento final: " + e.getMessage());
        }
    }
}
