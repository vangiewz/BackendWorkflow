package backendworkflow.backend.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String nombre;
    private String rol; // ADMIN, FUNCIONARIO, CLIENTE
    private String departamentoId;
    private String telefono;
    private java.util.List<String> fcmTokens = new java.util.ArrayList<>();
    private boolean isActive;

    public Usuario() {
    }

    // Getters y Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getDepartamentoId() {
        return departamentoId;
    }

    public void setDepartamentoId(String departamentoId) {
        this.departamentoId = departamentoId;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public java.util.List<String> getFcmTokens() {
        return fcmTokens;
    }

    public void setFcmTokens(java.util.List<String> fcmTokens) {
        this.fcmTokens = fcmTokens;
    }
}
