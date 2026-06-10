package backendworkflow.backend.usuario.service;


import backendworkflow.backend.bitacora.service.BitacoraService;
import backendworkflow.backend.departamento.model.Departamento;
import backendworkflow.backend.departamento.service.DepartamentoService;
import backendworkflow.backend.usuario.dto.AdminChangePasswordRequest;
import backendworkflow.backend.usuario.dto.ChangePasswordRequest;
import backendworkflow.backend.usuario.dto.ChangeRolRequest;
import backendworkflow.backend.usuario.dto.CreateUsuarioRequest;
import backendworkflow.backend.usuario.dto.UsuarioResponse;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartamentoService departamentoService;
    private final BitacoraService bitacoraService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            DepartamentoService departamentoService,
            BitacoraService bitacoraService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.departamentoService = departamentoService;
        this.bitacoraService = bitacoraService;
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

        bitacoraService.registrarAccion("CREACION_USUARIO", saved.getEmail(), saved.getNombre(), saved.getRol(), "Se ha creado un nuevo usuario interno en el sistema");

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
     * Asigna o limpia el departamento de un usuario.
     * Si departamentoId es null/blank, se elimina la asignación actual.
     *
     * @param usuarioId       ID del usuario a modificar
     * @param departamentoId  ID del departamento a asignar, o null para limpiar
     * @return UsuarioResponse actualizado
     */
    public UsuarioResponse assignDepartamento(String usuarioId, String departamentoId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (departamentoId == null || departamentoId.isBlank()) {
            usuario.setDepartamentoId(null);
        } else {
            departamentoService.findById(departamentoId)
                    .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
            usuario.setDepartamentoId(departamentoId);
        }

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

        bitacoraService.registrarAccion("CAMBIO_PASSWORD", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "El usuario ha cambiado su propia contraseña");
    }

    /**
     * Retorna todos los usuarios mapeados a UsuarioResponse.
     */
    public List<UsuarioResponse> findAllUsuarios() {
        return usuarioRepository.findAll().stream()
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
     * Si el nuevo rol es CLIENTE, se limpia el departamentoId automáticamente.
     */
    public UsuarioResponse changeRol(String id, ChangeRolRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setRol(request.rol());

        // Los clientes no pertenecen a ningún departamento
        if ("CLIENTE".equalsIgnoreCase(request.rol())) {
            usuario.setDepartamentoId(null);
        }

        Usuario saved = usuarioRepository.save(usuario);
        
        bitacoraService.registrarAccion("CAMBIO_ROL", saved.getEmail(), saved.getNombre(), saved.getRol(), "Rol actualizado por un administrador");

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

        bitacoraService.registrarAccion("CAMBIO_PASSWORD_ADMIN", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "Un administrador cambió la contraseña de este usuario");
    }

    /**
     * Ejecuta un borrado suave del usuario (lo inactiva).
     */
    public void deleteUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        usuario.setActive(false);
        usuarioRepository.save(usuario);

        bitacoraService.registrarAccion("DESACTIVACION_USUARIO", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "El usuario fue desactivado (borrado lógico)");
    }

    /**
     * Reactiva a un usuario inactivo.
     */
    public void reactivateUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        usuario.setActive(true);
        usuarioRepository.save(usuario);

        bitacoraService.registrarAccion("REACTIVACION_USUARIO", usuario.getEmail(), usuario.getNombre(), usuario.getRol(), "El usuario fue reactivado en el sistema");
    }
}
