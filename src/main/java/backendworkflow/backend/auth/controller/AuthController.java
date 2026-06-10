package backendworkflow.backend.auth.controller;


import backendworkflow.backend.auth.dto.AuthResponse;
import backendworkflow.backend.auth.dto.LoginRequest;
import backendworkflow.backend.auth.dto.RegisterRequest;
import backendworkflow.backend.auth.service.AuthService;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Controller de autenticación con endpoints separados para empleados y clientes.
 *
 * Endpoints públicos (no requieren JWT):
 * - POST /api/auth/empleado/login  → Login de usuarios internos (colección "usuarios")
 * - POST /api/auth/cliente/login   → Login de clientes externos (colección "clientes")
 * - POST /api/auth/cliente/register → Registro exclusivo para clientes
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login para empleados internos (ADMIN, FUNCIONARIO).
     * Solo busca credenciales en la colección "usuarios".
     */
    @PostMapping("/empleado/login")
    public ResponseEntity<?> loginEmpleado(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.loginEmpleado(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Login para clientes externos.
     * Solo busca credenciales en la colección "clientes".
     */
    @PostMapping("/cliente/login")
    public ResponseEntity<?> loginCliente(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.loginCliente(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Login global estructurado para la App Móvil.
     * Soporta ingreso de cualquier rol y captura de FCM token.
     */
    @PostMapping("/mobile/login")
    public ResponseEntity<?> loginMobile(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.loginMobile(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Registro exclusivo para nuevos clientes.
     * Retorna un JWT válido (auto-login tras registro exitoso).
     */
    @PostMapping("/cliente/register")
    public ResponseEntity<?> registerCliente(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.registerCliente(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
