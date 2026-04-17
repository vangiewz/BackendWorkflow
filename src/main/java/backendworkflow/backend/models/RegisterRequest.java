package backendworkflow.backend.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para registro de nuevos clientes.
 */
public record RegisterRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        String telefono
) {
}
