package backendworkflow.backend.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backendworkflow.backend.models.CreateUsuarioRequest;
import backendworkflow.backend.models.ChangePasswordRequest;
import backendworkflow.backend.models.AdminChangePasswordRequest;
import backendworkflow.backend.models.ChangeRolRequest;
import backendworkflow.backend.models.Usuario;
import backendworkflow.backend.models.UsuarioResponse;
import backendworkflow.backend.repositories.UsuarioRepository;

import backendworkflow.backend.models.ChangePasswordRequest;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartamentoService departamentoService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            DepartamentoService departamentoService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.departamentoService = departamentoService;
    }

    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    public Optional<Usuario> findById(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario save(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    /**
     * Crea un nuevo usuario interno (empleado/administrador).
     * La contraseña se hashea con BCrypt antes de persistir.
     *
     * @param request Datos del nuevo usuario
     * @return UsuarioResponse sin el hash del password
     * @throws RuntimeException si el email ya está registrado
     */
    public UsuarioResponse createUsuario(CreateUsuarioRequest request) {
        if (existsByEmail(request.email())) {
            throw new RuntimeException("Ya existe un usuario registrado con ese email");
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(request.email());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombre());
        usuario.setRol(request.rol());
        usuario.setDepartamentoId(request.departamentoId());
        usuario.setTelefono(request.telefono());
        usuario.setActive(true);

        Usuario saved = usuarioRepository.save(usuario);

        return new UsuarioResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getNombre(),
                saved.getRol(),
                saved.getDepartamentoId(),
                saved.getTelefono(),
                saved.isActive()
        );
    }

    /**
     * Asigna un departamento a un usuario existente.
     * Valida que tanto el usuario como el departamento existan.
     *
     * @param usuarioId       ID del usuario a modificar
     * @param departamentoId  ID del departamento a asignar
     * @return UsuarioResponse actualizado
     * @throws RuntimeException si el usuario o departamento no existen
     */
    public UsuarioResponse assignDepartamento(String usuarioId, String departamentoId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        departamentoService.findById(departamentoId)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        usuario.setDepartamentoId(departamentoId);
        Usuario saved = usuarioRepository.save(usuario);

        return new UsuarioResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getNombre(),
                saved.getRol(),
                saved.getDepartamentoId(),
                saved.getTelefono(),
                saved.isActive()
        );
    }

    /**
     * Cambia la contraseña de un usuario verificando primero la actual.
     *
     * @param email Email del usuario que cambia la contraseña
     * @param request Datos del cambio de contraseña
     * @throws RuntimeException si la contraseña actual no coincide
     */
    public void changePassword(String email, ChangePasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), usuario.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }

        usuario.setPassword(passwordEncoder.encode(request.newPassword()));
        usuarioRepository.save(usuario);
    }

    /**
     * Retorna todos los usuarios mapeados a UsuarioResponse.
     */
    public List<UsuarioResponse> findAllUsuarios() {
        return usuarioRepository.findAll().stream()
                .filter(u -> !"CLIENTE".equals(u.getRol()))
                .map(u -> new UsuarioResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getNombre(),
                        u.getRol(),
                        u.getDepartamentoId(),
                        u.getTelefono(),
                        u.isActive()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Permite a un administrador cambiar el rol de un usuario.
     */
    public UsuarioResponse changeRol(String id, ChangeRolRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setRol(request.rol());
        Usuario saved = usuarioRepository.save(usuario);
        
        return new UsuarioResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getNombre(),
                saved.getRol(),
                saved.getDepartamentoId(),
                saved.getTelefono(),
                saved.isActive()
        );
    }

    /**
     * Modifica el telefono de un usuario.
     */
    public UsuarioResponse changeTelefono(String id, String telefono) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setTelefono(telefono);
        Usuario saved = usuarioRepository.save(usuario);
        
        return new UsuarioResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getNombre(),
                saved.getRol(),
                saved.getDepartamentoId(),
                saved.getTelefono(),
                saved.isActive()
        );
    }

    /**
     * Permite a un administrador cambiar la contraseña de un usuario sin conocer la actual.
     */
    public void adminChangePassword(String id, AdminChangePasswordRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        usuario.setPassword(passwordEncoder.encode(request.newPassword()));
        usuarioRepository.save(usuario);
    }

    /**
     * Ejecuta un borrado suave del usuario (lo inactiva).
     */
    public void deleteUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        usuario.setActive(false);
        usuarioRepository.save(usuario);
    }

    /**
     * Reactiva a un usuario inactivo.
     */
    public void reactivateUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        usuario.setActive(true);
        usuarioRepository.save(usuario);
    }
}
