package backendworkflow.backend.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "La contraseña actual es requerida")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es requerida")
        @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
        String newPassword
) {
}
