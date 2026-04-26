package backendworkflow.backend.repositories;

import backendworkflow.backend.models.Bitacora;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BitacoraRepository extends MongoRepository<Bitacora, String> {
    List<Bitacora> findAllByOrderByFechaHoraDesc();
}
