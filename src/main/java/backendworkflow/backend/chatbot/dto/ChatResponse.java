package backendworkflow.backend.chatbot.dto;

public class ChatResponse {
    private String tramiteId;
    private String mensaje;

    public ChatResponse() {}

    public ChatResponse(String tramiteId, String mensaje) {
        this.tramiteId = tramiteId;
        this.mensaje = mensaje;
    }

    public String getTramiteId() {
        return tramiteId;
    }

    public void setTramiteId(String tramiteId) {
        this.tramiteId = tramiteId;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
