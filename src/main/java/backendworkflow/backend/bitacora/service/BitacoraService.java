package backendworkflow.backend.bitacora.service;


import backendworkflow.backend.bitacora.model.Bitacora;
import backendworkflow.backend.bitacora.repository.BitacoraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BitacoraService {

    @Autowired
    private BitacoraRepository bitacoraRepository;

    public void registrarAccion(String accion, String usuarioEmail, String usuarioNombre, String rol, String detalles) {
        Bitacora bitacora = new Bitacora(accion, usuarioEmail, usuarioNombre, rol, detalles);
        bitacoraRepository.save(bitacora);
    }

    public List<Bitacora> obtenerTodas() {
        return bitacoraRepository.findAllByOrderByFechaHoraDesc();
    }
}
