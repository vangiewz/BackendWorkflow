package backendworkflow.backend.notificacion.service;


import backendworkflow.backend.departamento.model.Departamento;
import backendworkflow.backend.departamento.repository.DepartamentoRepository;
import backendworkflow.backend.tramite.model.PasoWorkflow;
import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationStepService {

    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FirebaseMessagingService firebaseMessagingService;

    public NotificationStepService(
            DepartamentoRepository departamentoRepository,
            UsuarioRepository usuarioRepository,
            FirebaseMessagingService firebaseMessagingService) {
        this.departamentoRepository = departamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.firebaseMessagingService = firebaseMessagingService;
    }

    /**
     * Determina quién es el responsable del paso y envía la notificación push correspondiente.
     */
    public void notificarSiguientePaso(Tramite tramite, PasoWorkflow pasoSiguiente) {
        String departamentoId = pasoSiguiente.departamentoId();

        if (departamentoId == null || departamentoId.isEmpty()) {
            // El paso pertenece al cliente
            Usuario cliente = usuarioRepository.findById(tramite.getClienteId()).orElse(null);
            if (cliente != null && cliente.getFcmTokens() != null && !cliente.getFcmTokens().isEmpty()) {
                String titulo = "Acción Requerida en tu Trámite";
                String mensaje = "El trámite '" + tramite.getNombrePlantilla() + "' requiere tu atención en el paso: " + pasoSiguiente.nombrePaso();
                
                // Enviar notificación al cliente (puede tener múltiples tokens si usa varios dispositivos)
                firebaseMessagingService.sendNotificationToMultipleTokens(cliente.getFcmTokens(), titulo, mensaje);
            }
        } else {
            // El paso pertenece a un departamento específico (funcionarios)
            Departamento depto = departamentoRepository.findById(departamentoId).orElse(null);
            if (depto != null) {
                // Buscar todos los usuarios (funcionarios) que pertenecen a este departamento
                List<Usuario> funcionarios = usuarioRepository.findByDepartamentoId(departamentoId);
                
                String titulo = "Nuevo Trámite Asignado: " + depto.getNombre();
                String mensaje = "El trámite '" + tramite.getNombrePlantilla() + "' ha llegado al paso: " + pasoSiguiente.nombrePaso();

                for (Usuario funcionario : funcionarios) {
                    if (funcionario.getFcmTokens() != null && !funcionario.getFcmTokens().isEmpty()) {
                        firebaseMessagingService.sendNotificationToMultipleTokens(funcionario.getFcmTokens(), titulo, mensaje);
                    }
                }
            }
        }
    }
}
