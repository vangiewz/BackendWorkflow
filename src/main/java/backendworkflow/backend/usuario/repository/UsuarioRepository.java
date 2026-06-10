package backendworkflow.backend.usuario.repository;


import backendworkflow.backend.usuario.model.Usuario;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDepartamentoId(String departamentoId);

    List<Usuario> findByDepartamentoId(String departamentoId);
}
