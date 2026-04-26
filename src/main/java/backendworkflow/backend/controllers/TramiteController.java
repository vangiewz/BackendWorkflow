package backendworkflow.backend.controllers;

import backendworkflow.backend.models.AsistenteFormularioRequest;
import backendworkflow.backend.models.AsistenteFormularioResponse;
import backendworkflow.backend.models.Tramite;
import backendworkflow.backend.models.PlantillaWorkflow;
import backendworkflow.backend.models.PasoWorkflow;
import backendworkflow.backend.models.Usuario;
import backendworkflow.backend.services.ClaudeAiService;
import backendworkflow.backend.services.TramiteService;
import backendworkflow.backend.services.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

    private final TramiteService tramiteService;
    private final UsuarioService usuarioService;
    private final ClaudeAiService claudeAiService;
    private final ObjectMapper objectMapper;

    public TramiteController(TramiteService tramiteService, UsuarioService usuarioService, ClaudeAiService claudeAiService) {
        this.tramiteService = tramiteService;
        this.usuarioService = usuarioService;
        this.claudeAiService = claudeAiService;
        this.objectMapper = new ObjectMapper();
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
     * Iniciar un nuevo trámite a partir de una plantilla (solo JSON).
     */
    @PostMapping(consumes = "application/json")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> iniciarJson(@RequestBody Map<String, Object> body) {
        try {
            String plantillaId = (String) body.get("plantillaId");
            String clienteId = (String) body.get("clienteId");
            @SuppressWarnings("unchecked")
            Map<String, Object> datosCliente = (Map<String, Object>) body.get("datosCliente");

            Tramite tramite = tramiteService.iniciarTramiteMultipart(plantillaId, clienteId, datosCliente, null);
            return ResponseEntity.status(HttpStatus.CREATED).body(tramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Iniciar un nuevo trámite a partir de una plantilla (con archivos adjuntos).
     */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> iniciarMultipart(
            @RequestPart("datos") String datosJson,
            org.springframework.web.multipart.MultipartHttpServletRequest request) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = mapper.readValue(datosJson, Map.class);

            String plantillaId = (String) dataMap.get("plantillaId");
            String clienteId = (String) dataMap.get("clienteId");
            @SuppressWarnings("unchecked")
            Map<String, Object> datosCliente = (Map<String, Object>) dataMap.get("datosCliente");

            java.util.Map<String, org.springframework.web.multipart.MultipartFile> archivos = request.getFileMap();

            Tramite tramite = tramiteService.iniciarTramiteMultipart(plantillaId, clienteId, datosCliente, archivos);
            return ResponseEntity.status(HttpStatus.CREATED).body(tramite);
        } catch (Exception e) {
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
     * Responder un paso del trámite (solo JSON, sin archivos).
     */
    @PostMapping(value = "/{id}/responder", consumes = "application/json")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> responderJson(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            String pasoId = (String) body.get("pasoId");
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = (Map<String, Object>) body.get("respuesta");
            String decisionElegida = (String) body.get("decisionElegida");

            String email = authentication.getName();
            Usuario usuario = usuarioService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            String deptoId = usuario.getDepartamentoId();

            Tramite tramite = tramiteService.responderPaso(
                    id, pasoId, usuario.getId(), usuario.getNombre(),
                    deptoId, respuesta, decisionElegida, null
            );

            return ResponseEntity.ok(tramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Responder un paso del trámite (con archivos adjuntos, multipart).
     */
    @PostMapping(value = "/{id}/responder", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> responderMultipart(
            @PathVariable String id, 
            @RequestPart("datos") String datosJson,
            org.springframework.web.multipart.MultipartHttpServletRequest request,
            Authentication authentication) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(datosJson, Map.class);

            String pasoId = (String) body.get("pasoId");
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = (Map<String, Object>) body.get("respuesta");
            String decisionElegida = (String) body.get("decisionElegida");

            String email = authentication.getName();
            Usuario usuario = usuarioService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            String deptoId = usuario.getDepartamentoId();

            java.util.Map<String, org.springframework.web.multipart.MultipartFile> archivos = request.getFileMap();

            Tramite tramite = tramiteService.responderPaso(
                    id, pasoId, usuario.getId(), usuario.getNombre(),
                    deptoId, respuesta, decisionElegida, archivos
            );

            return ResponseEntity.ok(tramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/asistir-formulario")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> asistirFormulario(
            @PathVariable String id,
            @RequestBody AsistenteFormularioRequest request,
            Authentication authentication
    ) {
        try {
            String email = authentication.getName();
            Usuario usuario = usuarioService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            AsistenteFormularioResponse response = tramiteService.asistirFormulario(
                    id,
                    request.pasoId(),
                    request.modo(),
                    request.mensaje(),
                    usuario.getId(),
                    usuario.getDepartamentoId()
            );

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Genera un resumen del trámite con IA para el cliente.
     * Solo disponible para trámites finalizados.
     */
    @GetMapping("/{id}/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<?> getResumenTramite(@PathVariable String id) {
        try {
            Tramite tramite = tramiteService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

            if (!"FINALIZADO".equals(tramite.getEstadoGlobal())) {
                return ResponseEntity.badRequest().body(Map.of("error", "El trámite aún no ha finalizado"));
            }

            // Compactar datos del trámite (sin info sensible ni archivos)
            String tramiteCompacto = compactarTramite(tramite);

            String resumenJson = claudeAiService.resumirTramite(tramiteCompacto);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resumenJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Compacta los datos de un trámite eliminando archivos, IDs internos y datos sensibles.
     */
    private String compactarTramite(Tramite tramite) {
        Map<String, Object> compacto = new HashMap<>();
        compacto.put("nombreTramite", tramite.getNombrePlantilla());
        compacto.put("estado", tramite.getEstadoGlobal());

        // Datos del formulario cliente (sin archivos)
        if (tramite.getDatosFormularioCliente() != null) {
            Map<String, Object> datosLimpios = new HashMap<>();
            tramite.getDatosFormularioCliente().forEach((key, value) -> {
                if (value != null && !looksLikeFile(value.toString())) {
                    datosLimpios.put(key, value);
                }
            });
            compacto.put("datosSolicitud", datosLimpios);
        }

        // Respuestas de cada paso (sin archivos ni IDs)
        if (tramite.getRespuestas() != null && !tramite.getRespuestas().isEmpty()) {
            Map<String, Map<String, Object>> respuestasLimpias = new HashMap<>();
            tramite.getRespuestas().forEach((pasoId, respuesta) -> {
                Map<String, Object> respuestaLimpia = new HashMap<>();
                respuesta.forEach((key, value) -> {
                    if (value != null && !looksLikeFile(value.toString())) {
                        respuestaLimpia.put(key, value);
                    }
                });
                if (!respuestaLimpia.isEmpty()) {
                    respuestasLimpias.put(pasoId, respuestaLimpia);
                }
            });
            compacto.put("respuestas", respuestasLimpias);
        }

        try {
            return objectMapper.writeValueAsString(compacto);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean looksLikeFile(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.startsWith("http") || lower.endsWith(".pdf") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".doc")
                || lower.endsWith(".docx") || lower.contains("blob.core.windows.net")
                || lower.startsWith("data:");
    }
}
