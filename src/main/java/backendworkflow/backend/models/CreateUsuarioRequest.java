package backendworkflow.backend.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creación de usuarios internos (empleados).
 * Solo un ADMIN puede usar este endpoint.
 */
public record CreateUsuarioRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "ADMIN|FUNCIONARIO|CLIENTE", message = "El rol debe ser ADMIN, FUNCIONARIO o CLIENTE")
        String rol,

        String departamentoId,
        
        String telefono
) {
}
