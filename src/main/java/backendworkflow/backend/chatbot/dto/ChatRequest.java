package backendworkflow.backend.chatbot.dto;

public class ChatRequest {
    private String mensajeUsuario;

    public ChatRequest() {}

    public ChatRequest(String mensajeUsuario) {
        this.mensajeUsuario = mensajeUsuario;
    }

    public String getMensajeUsuario() {
        return mensajeUsuario;
    }

    public void setMensajeUsuario(String mensajeUsuario) {
        this.mensajeUsuario = mensajeUsuario;
    }
}
