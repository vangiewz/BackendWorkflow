package backendworkflow.backend.bitacora.repository;


import backendworkflow.backend.bitacora.model.Bitacora;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BitacoraRepository extends MongoRepository<Bitacora, String> {
    List<Bitacora> findAllByOrderByFechaHoraDesc();
}
