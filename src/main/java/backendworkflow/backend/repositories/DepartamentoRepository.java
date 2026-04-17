package backendworkflow.backend.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import backendworkflow.backend.models.Departamento;

public interface DepartamentoRepository extends MongoRepository<Departamento, String> {

    Optional<Departamento> findByNombre(String nombre);
}
