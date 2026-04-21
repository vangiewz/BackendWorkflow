package backendworkflow.backend.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backendworkflow.backend.models.CreateUsuarioRequest;
import backendworkflow.backend.models.ChangeRolRequest;
import backendworkflow.backend.models.AdminChangePasswordRequest;
import backendworkflow.backend.models.UsuarioResponse;
import backendworkflow.backend.models.ChangePasswordRequest;
import backendworkflow.backend.services.UsuarioService;
import jakarta.validation.Valid;
import java.security.Principal;

/**
 * Controller para la gestión de usuarios internos (empleados).
 * Todos los endpoints requieren rol ADMIN.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Crea un nuevo usuario interno (empleado o administrador).
     * Solo accesible para usuarios con rol ADMIN.
     *
     * El rol del nuevo usuario puede ser ADMIN o FUNCIONARIO.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUsuario(@Valid @RequestBody CreateUsuarioRequest request) {
        try {
            UsuarioResponse response = usuarioService.createUsuario(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Asigna un departamento a un usuario existente.
     * Solo accesible para usuarios con rol ADMIN.
     */
    @PatchMapping("/{id}/departamento")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assignDepartamento(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        try {
            String departamentoId = body.get("departamentoId");
            if (departamentoId == null || departamentoId.isBlank()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "El departamentoId es obligatorio"));
            }
            UsuarioResponse response = usuarioService.assignDepartamento(id, departamentoId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un empleado cambiar su propia contraseña.
     * Universal para cualquier usuario logueado.
     */
    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        try {
            usuarioService.changePassword(principal.getName(), request);
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    /**
     * Permite a cualquier usuario cambiar su propio teléfono.
     */
    @PatchMapping("/me/telefono")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changeMyTelefono(
            Principal principal,
            @RequestBody Map<String, String> body
    ) {
        try {
            String telefono = body.get("telefono");
            
            // Buscar al usuario por el email del token para obtener su ID
            backendworkflow.backend.models.Usuario user = usuarioService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
            UsuarioResponse response = usuarioService.changeTelefono(user.getId(), telefono);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    /**
     * Obtiene todos los usuarios internos (empleados).
     * Solo accesible para usuarios con rol ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<UsuarioResponse>> getAllUsuarios() {
        return ResponseEntity.ok(usuarioService.findAllUsuarios());
    }

    /**
     * Obtiene un usuario especifico.
     * Accesible por backend para identificar a los iniciadores de los tramites.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> getUsuarioById(@PathVariable String id) {
        try {
            backendworkflow.backend.models.Usuario user = usuarioService.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            // Map Entity to Response
            UsuarioResponse response = new UsuarioResponse(
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getRol(),
                user.getDepartamentoId(),
                user.getTelefono(),
                user.isActive()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un ADMIN cambiar el rol de cualquier usuario.
     */
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changeRol(
            @PathVariable String id,
            @Valid @RequestBody ChangeRolRequest request
    ) {
        try {
            UsuarioResponse response = usuarioService.changeRol(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un ADMIN cambiar la contraseña de cualquier usuario.
     */
    @PatchMapping("/{id}/admin-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminChangePassword(
            @PathVariable String id,
            @Valid @RequestBody AdminChangePasswordRequest request
    ) {
        try {
            usuarioService.adminChangePassword(id, request);
            return ResponseEntity.ok(Map.of("message", "Contraseña del usuario actualizada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un ADMIN cambiar el telefono de cualquier usuario.
     */
    @PatchMapping("/{id}/telefono")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changeTelefono(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        try {
            String telefono = body.get("telefono");
            UsuarioResponse response = usuarioService.changeTelefono(id, telefono);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Soft-delete (desactiva) a un usuario.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUsuario(@PathVariable String id) {
        try {
            usuarioService.deleteUsuario(id);
            return ResponseEntity.ok(Map.of("message", "Usuario desactivado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reactiva a un usuario.
     */
    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reactivateUsuario(@PathVariable String id) {
        try {
            usuarioService.reactivateUsuario(id);
            return ResponseEntity.ok(Map.of("message", "Usuario reactivado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
