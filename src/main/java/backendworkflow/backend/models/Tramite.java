package backendworkflow.backend.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    private String plantillaId;
    private String clienteId;
    private String estadoGlobal; // PENDIENTE, EN_PROGRESO, FINALIZADO o RECHAZADO
    private Integer pasoActualOrden;
    private Map<String, Object> datosFormulario;
    private List<RegistroTiempo> historialTiempos;

    public Tramite() {
    }

    // Getters y Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlantillaId() {
        return plantillaId;
    }

    public void setPlantillaId(String plantillaId) {
        this.plantillaId = plantillaId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getEstadoGlobal() {
        return estadoGlobal;
    }

    public void setEstadoGlobal(String estadoGlobal) {
        this.estadoGlobal = estadoGlobal;
    }

    public Integer getPasoActualOrden() {
        return pasoActualOrden;
    }

    public void setPasoActualOrden(Integer pasoActualOrden) {
        this.pasoActualOrden = pasoActualOrden;
    }

    public Map<String, Object> getDatosFormulario() {
        return datosFormulario;
    }

    public void setDatosFormulario(Map<String, Object> datosFormulario) {
        this.datosFormulario = datosFormulario;
    }

    public List<RegistroTiempo> getHistorialTiempos() {
        return historialTiempos;
    }

    public void setHistorialTiempos(List<RegistroTiempo> historialTiempos) {
        this.historialTiempos = historialTiempos;
    }
}
