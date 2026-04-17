package backendworkflow.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import backendworkflow.backend.models.CreateDepartamentoRequest;
import backendworkflow.backend.models.Departamento;
import backendworkflow.backend.repositories.DepartamentoRepository;
import backendworkflow.backend.repositories.UsuarioRepository;
import backendworkflow.backend.models.UpdateDepartamentoRequest;

@Service
public class DepartamentoService {

    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public DepartamentoService(DepartamentoRepository departamentoRepository, UsuarioRepository usuarioRepository) {
        this.departamentoRepository = departamentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Crea un nuevo departamento.
     *
     * @param request Datos del departamento
     * @return Departamento creado con su ID generado
     * @throws RuntimeException si ya existe un departamento con ese nombre
     */
    public Departamento createDepartamento(CreateDepartamentoRequest request) {
        if (departamentoRepository.findByNombre(request.nombre()).isPresent()) {
            throw new RuntimeException("Ya existe un departamento con ese nombre");
        }

        Departamento departamento = new Departamento(request.nombre());
        return departamentoRepository.save(departamento);
    }

    /**
     * Retorna todos los departamentos.
     */
    public List<Departamento> findAll() {
        return departamentoRepository.findAll();
    }

    /**
     * Busca un departamento por su ID.
     * Reutilizable por otros servicios para validar existencia.
     */
    public Optional<Departamento> findById(String id) {
        return departamentoRepository.findById(id);
    }

    /**
     * Actualiza el nombre de un departamento.
     */
    public Departamento updateDepartamento(String id, UpdateDepartamentoRequest request) {
        Departamento departamento = departamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        departamento.setNombre(request.nombre());
        return departamentoRepository.save(departamento);
    }

    /**
     * Elimina un departamento verificando que no existan usuarios asignados a él.
     */
    public void deleteDepartamento(String id) {
        if (!departamentoRepository.existsById(id)) {
            throw new RuntimeException("Departamento no encontrado");
        }
        
        if (usuarioRepository.existsByDepartamentoId(id)) {
            throw new RuntimeException("No se puede eliminar el departamento porque existen usuarios asignados a él");
        }
        
        departamentoRepository.deleteById(id);
    }
}
