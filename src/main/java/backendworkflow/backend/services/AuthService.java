package backendworkflow.backend.services;

import java.util.ArrayList;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backendworkflow.backend.models.AuthResponse;
import backendworkflow.backend.models.LoginRequest;
import backendworkflow.backend.models.RegisterRequest;
import backendworkflow.backend.models.Usuario;
import backendworkflow.backend.security.JwtService;

/**
 * Servicio de autenticación que orquesta el login y registro.
 *
 * Decisiones de diseño:
 * - Login de empleados busca SOLO en la colección "usuarios"
 * - Login de clientes busca SOLO en la colección "clientes"
 * - El registro solo está disponible para clientes (los empleados se crean manualmente o por un admin)
 * - Se comunica con UsuarioService y ClienteService (nunca accede a repositorios directamente)
 */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioService usuarioService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Login para usuarios internos (empleados/administradores).
     * Busca exclusivamente en la colección "usuarios".
     *
     * @param request Email y password
     * @return AuthResponse con JWT que incluye tipoUsuario="USUARIO"
     * @throws RuntimeException si las credenciales son inválidas o la cuenta está desactivada
     */
    public AuthResponse loginEmpleado(LoginRequest request) {
        Usuario usuario = usuarioService.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!usuario.isActive()) {
            throw new RuntimeException("La cuenta del empleado está desactivada");
        }

        if ("CLIENTE".equals(usuario.getRol())) {
            throw new RuntimeException("Acceso denegado. Utilice la aplicación móvil para iniciar sesión.");
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        String token = jwtService.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol(),
                "USUARIO"
        );

        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getDepartamentoId(),
                "USUARIO"
        );
    }

    /**
     * Login para clientes externos.
     * Busca exclusivamente en la colección "clientes".
     *
     * @param request Email y password
     * @return AuthResponse con JWT que incluye tipoUsuario="CLIENTE"
     * @throws RuntimeException si las credenciales son inválidas o la cuenta está desactivada
     */
    public AuthResponse loginCliente(LoginRequest request) {
        Usuario usuario = usuarioService.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!usuario.isActive()) {
            throw new RuntimeException("La cuenta del cliente está desactivada");
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        String token = jwtService.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol(),
                "CLIENTE"
        );

        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getDepartamentoId(),
                "CLIENTE"
        );
    }

    /**
     * Login global para la aplicación móvil.
     * Permite loguearse a ADMIN, FUNCIONARIO y CLIENTE.
     * Guarda el fcmToken si es proporcionado (para notificaciones Push).
     *
     * @param request Email, password y opcionalmente fcmToken
     * @return AuthResponse con JWT
     */
    public AuthResponse loginMobile(LoginRequest request) {
        Usuario usuario = usuarioService.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!usuario.isActive()) {
            throw new RuntimeException("La cuenta del usuario está desactivada");
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        // Si Envía un FCM token, lo guardamos si no estaba ya
        if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
            if (usuario.getFcmTokens() == null) {
                usuario.setFcmTokens(new ArrayList<>());
            }
            if (!usuario.getFcmTokens().contains(request.fcmToken())) {
                usuario.getFcmTokens().add(request.fcmToken());
                usuarioService.save(usuario);
            }
        }

        String tipoUsuario = "CLIENTE".equals(usuario.getRol()) ? "CLIENTE" : "USUARIO";
        String token = jwtService.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol(),
                tipoUsuario
        );

        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getDepartamentoId(),
                tipoUsuario
        );
    }

    /**
     * Registro exclusivo para nuevos clientes.
     * Hashea la contraseña con BCrypt antes de persistir.
     *
     * @param request Datos del nuevo cliente
     * @return AuthResponse con JWT generado automáticamente (login tras registro)
     * @throws RuntimeException si el email ya está registrado
     */
    public AuthResponse registerCliente(RegisterRequest request) {
        if (usuarioService.existsByEmail(request.email())) {
            throw new RuntimeException("Ya existe un cliente registrado con ese email");
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(request.email());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombre());
        usuario.setTelefono(request.telefono());
        usuario.setRol("CLIENTE");
        usuario.setDepartamentoId(null);
        usuario.setActive(true);

        Usuario savedUsuario = usuarioService.save(usuario);

        String token = jwtService.generateToken(
                savedUsuario.getId(),
                savedUsuario.getEmail(),
                "CLIENTE",
                "CLIENTE"
        );

        return new AuthResponse(
                token,
                savedUsuario.getId(),
                savedUsuario.getEmail(),
                savedUsuario.getNombre(),
                "CLIENTE",
                null,
                "CLIENTE"
        );
    }
}
