package backendworkflow.backend.auth.service;


import backendworkflow.backend.auth.dto.AuthResponse;
import backendworkflow.backend.auth.dto.LoginRequest;
import backendworkflow.backend.auth.dto.RegisterRequest;
import backendworkflow.backend.bitacora.service.BitacoraService;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.service.UsuarioService;
import java.util.ArrayList;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
    private final BitacoraService bitacoraService;

    public AuthService(
            UsuarioService usuarioService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            BitacoraService bitacoraService
    ) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.bitacoraService = bitacoraService;
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

        bitacoraService.registrarAccion("LOGIN_WEB_EMPLEADO", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "Inicio de sesión web exitoso");

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

        bitacoraService.registrarAccion("LOGIN_WEB_CLIENTE", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "Inicio de sesión web exitoso");

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

        // Reemplazar el FCM token en cada login (siempre 1 token activo por usuario)
        if (request.fcmToken() != null && !request.fcmToken().isBlank()
                && !request.fcmToken().startsWith("dummy")) {
            ArrayList<String> tokens = new ArrayList<>();
            tokens.add(request.fcmToken());
            usuario.setFcmTokens(tokens);
            usuarioService.save(usuario);
        }

        String tipoUsuario = "CLIENTE".equals(usuario.getRol()) ? "CLIENTE" : "USUARIO";
        String token = jwtService.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol(),
                tipoUsuario
        );

        bitacoraService.registrarAccion("LOGIN_MOVIL", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "Inicio de sesión móvil exitoso");

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

        bitacoraService.registrarAccion("REGISTRO_CLIENTE", savedUsuario.getEmail(), savedUsuario.getNombre(), "CLIENTE", "Nuevo cliente registrado en el sistema");

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
