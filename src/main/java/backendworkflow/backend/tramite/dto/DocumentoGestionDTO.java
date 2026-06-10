package backendworkflow.backend.tramite.dto;

import java.time.LocalDateTime;

public class DocumentoGestionDTO {

    private String archivoId;
    private String nombreOriginal;
    private String rutaS3;
    private String tramiteId;
    private String tramiteNombre;
    private String pasoId;
    private String pasoNombre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime tramiteFechaCreacion;
    private String departamentoGeneradorId;
    private boolean editable;
    private String extension;
    private boolean esColaborativo;

    private String clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private String campoKey;
    private java.util.List<backendworkflow.backend.tramite.model.ArchivoMetadata> historialVersiones;

    public DocumentoGestionDTO() {
    }

    public DocumentoGestionDTO(String archivoId, String nombreOriginal, String rutaS3, String tramiteId, 
                               String tramiteNombre, String pasoId, String pasoNombre, 
                               LocalDateTime fechaCreacion, LocalDateTime tramiteFechaCreacion, String departamentoGeneradorId, 
                               boolean editable, String extension, boolean esColaborativo) {
        this.archivoId = archivoId;
        this.nombreOriginal = nombreOriginal;
        this.rutaS3 = rutaS3;
        this.tramiteId = tramiteId;
        this.tramiteNombre = tramiteNombre;
        this.pasoId = pasoId;
        this.pasoNombre = pasoNombre;
        this.fechaCreacion = fechaCreacion;
        this.tramiteFechaCreacion = tramiteFechaCreacion;
        this.departamentoGeneradorId = departamentoGeneradorId;
        this.editable = editable;
        this.extension = extension;
        this.esColaborativo = esColaborativo;
    }

    // Getters and Setters

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }

    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }

    public String getRutaS3() { return rutaS3; }
    public void setRutaS3(String rutaS3) { this.rutaS3 = rutaS3; }

    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }

    public String getTramiteNombre() { return tramiteNombre; }
    public void setTramiteNombre(String tramiteNombre) { this.tramiteNombre = tramiteNombre; }

    public String getPasoId() { return pasoId; }
    public void setPasoId(String pasoId) { this.pasoId = pasoId; }

    public String getPasoNombre() { return pasoNombre; }
    public void setPasoNombre(String pasoNombre) { this.pasoNombre = pasoNombre; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getTramiteFechaCreacion() { return tramiteFechaCreacion; }
    public void setTramiteFechaCreacion(LocalDateTime tramiteFechaCreacion) { this.tramiteFechaCreacion = tramiteFechaCreacion; }

    public String getDepartamentoGeneradorId() { return departamentoGeneradorId; }
    public void setDepartamentoGeneradorId(String departamentoGeneradorId) { this.departamentoGeneradorId = departamentoGeneradorId; }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public boolean isEsColaborativo() { return esColaborativo; }
    public void setEsColaborativo(boolean esColaborativo) { this.esColaborativo = esColaborativo; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public String getCampoKey() { return campoKey; }
    public void setCampoKey(String campoKey) { this.campoKey = campoKey; }

    public java.util.List<backendworkflow.backend.tramite.model.ArchivoMetadata> getHistorialVersiones() { return historialVersiones; }
    public void setHistorialVersiones(java.util.List<backendworkflow.backend.tramite.model.ArchivoMetadata> historialVersiones) { this.historialVersiones = historialVersiones; }
}
