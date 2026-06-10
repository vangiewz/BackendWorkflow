package backendworkflow.backend.bitacora.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "bitacoras")
public class Bitacora {

    @Id
    private String id;
    
    private String accion;
    private String usuarioEmail;
    private String usuarioNombre;
    private String rol;
    private LocalDateTime fechaHora;
    private String detalles;

    public Bitacora() {
        this.fechaHora = LocalDateTime.now();
    }

    public Bitacora(String accion, String usuarioEmail, String usuarioNombre, String rol, String detalles) {
        this.accion = accion;
        this.usuarioEmail = usuarioEmail;
        this.usuarioNombre = usuarioNombre;
        this.rol = rol;
        this.detalles = detalles;
        this.fechaHora = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
}
