package backendworkflow.backend.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUsuarioRequest(
        @NotBlank(message = "El email es requerido")
        @Email(message = "Formato de email inválido")
        String email,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank(message = "El nombre es requerido")
        String nombre,

        @NotBlank(message = "El rol es requerido")
        String rol,

        String departamentoId,

        String telefono
) {
}
