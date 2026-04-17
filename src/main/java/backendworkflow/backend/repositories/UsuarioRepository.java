package backendworkflow.backend.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import backendworkflow.backend.models.Usuario;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDepartamentoId(String departamentoId);
}
