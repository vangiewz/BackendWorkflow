package backendworkflow.backend.departamento.repository;


import backendworkflow.backend.departamento.model.Departamento;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartamentoRepository extends MongoRepository<Departamento, String> {

    Optional<Departamento> findByNombre(String nombre);

    List<Departamento> findByIsActiveTrue();
}
