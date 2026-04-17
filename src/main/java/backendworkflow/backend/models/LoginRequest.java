package backendworkflow.backend.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitudes de login (empleados y clientes).
 */
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}
