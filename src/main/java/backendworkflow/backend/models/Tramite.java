package backendworkflow.backend.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    private String plantillaId;
    private String nombrePlantilla;
    private String clienteId;
    private String estadoGlobal; // PENDIENTE, EN_PROGRESO, FINALIZADO
    private String pasoActualId; // ID del paso activo en el grafo de la plantilla
    private Map<String, Object> datosFormularioCliente; // Datos iniciales del cliente
    private Map<String, Map<String, Object>> respuestas; // pasoId -> respuestas del formulario
    private List<RegistroTiempo> historialTiempos;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaFinalizacion;

    private String paymentId;
    private String invoiceUrl;
    private String clienteEmail;

    public Tramite() {
        this.respuestas = new HashMap<>();
        this.historialTiempos = new ArrayList<>();
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

    public String getNombrePlantilla() {
        return nombrePlantilla;
    }

    public void setNombrePlantilla(String nombrePlantilla) {
        this.nombrePlantilla = nombrePlantilla;
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

    public String getPasoActualId() {
        return pasoActualId;
    }

    public void setPasoActualId(String pasoActualId) {
        this.pasoActualId = pasoActualId;
    }

    public Map<String, Object> getDatosFormularioCliente() {
        return datosFormularioCliente;
    }

    public void setDatosFormularioCliente(Map<String, Object> datosFormularioCliente) {
        this.datosFormularioCliente = datosFormularioCliente;
    }

    public Map<String, Map<String, Object>> getRespuestas() {
        return respuestas;
    }

    public void setRespuestas(Map<String, Map<String, Object>> respuestas) {
        this.respuestas = respuestas;
    }

    public List<RegistroTiempo> getHistorialTiempos() {
        return historialTiempos;
    }

    public void setHistorialTiempos(List<RegistroTiempo> historialTiempos) {
        this.historialTiempos = historialTiempos;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaFinalizacion() {
        return fechaFinalizacion;
    }

    public void setFechaFinalizacion(LocalDateTime fechaFinalizacion) {
        this.fechaFinalizacion = fechaFinalizacion;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getInvoiceUrl() {
        return invoiceUrl;
    }

    public void setInvoiceUrl(String invoiceUrl) {
        this.invoiceUrl = invoiceUrl;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public void setClienteEmail(String clienteEmail) {
        this.clienteEmail = clienteEmail;
    }
}
