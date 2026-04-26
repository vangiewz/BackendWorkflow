package backendworkflow.backend.services;

import backendworkflow.backend.models.PasoWorkflow;
import backendworkflow.backend.models.Tramite;
import backendworkflow.backend.models.Usuario;
import backendworkflow.backend.repositories.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationStepService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationStepService.class);

    private final UsuarioRepository usuarioRepository;
    private final FirebasePushService firebasePushService;
    private final SendGridEmailService sendGridEmailService;

    public NotificationStepService(UsuarioRepository usuarioRepository,
                                   FirebasePushService firebasePushService,
                                   SendGridEmailService sendGridEmailService) {
        this.usuarioRepository = usuarioRepository;
        this.firebasePushService = firebasePushService;
        this.sendGridEmailService = sendGridEmailService;
    }

    /**
     * Notifica a los responsables del siguiente paso de un trámite.
     * ESTE método es @Async — todo lo que llama adentro es SÍNCRONO.
     */
    @Async
    public void notificarSiguientePaso(Tramite tramite, PasoWorkflow siguientePaso) {
        if (tramite == null || siguientePaso == null) {
            logger.warn("notificarSiguientePaso: tramite o paso nulo. Omitiendo.");
            return;
        }

        String nombreTramite = tramite.getNombrePlantilla() != null ? tramite.getNombrePlantilla() : "Trámite";
        String nombrePaso = siguientePaso.nombrePaso() != null ? siguientePaso.nombrePaso() : "Paso pendiente";
        String deptoId = siguientePaso.departamentoId();

        logger.info("=== NOTIFICACIÓN DE PASO === Trámite: '{}', Paso: '{}', DepartamentoId: '{}'",
                nombreTramite, nombrePaso, deptoId);

        try {
            if (deptoId != null && !deptoId.isBlank() && !deptoId.equalsIgnoreCase("Cliente") && !deptoId.equalsIgnoreCase("null")) {
                notificarDepartamento(tramite, deptoId, nombreTramite, nombrePaso);
            } else {
                notificarCliente(tramite, nombreTramite, nombrePaso);
            }
        } catch (Exception e) {
            logger.error("Error inesperado en notificación del paso '{}' del trámite '{}': {}",
                    nombrePaso, tramite.getId(), e.getMessage(), e);
        }
    }

    private void notificarDepartamento(Tramite tramite, String departamentoId,
                                        String nombreTramite, String nombrePaso) {
        logger.info("Buscando funcionarios del departamento '{}'...", departamentoId);
        List<Usuario> funcionarios = usuarioRepository.findByDepartamentoId(departamentoId);

        if (funcionarios == null || funcionarios.isEmpty()) {
            logger.warn("DEPARTAMENTO VACÍO: No hay funcionarios en departamento '{}' para paso '{}' del trámite '{}'.",
                    departamentoId, nombrePaso, tramite.getId());
            return;
        }

        logger.info("Encontrados {} funcionario(s) en departamento '{}'. Enviando notificaciones...",
                funcionarios.size(), departamentoId);

        String pushTitle = "Trámite: " + nombreTramite;
        String pushBody = "Se le ha asignado el paso \"" + nombrePaso + "\". Ingrese al sistema para procesarlo.";
        String emailSubject = "Nuevo paso asignado — " + nombreTramite;

        int emailOk = 0, emailFail = 0, pushOk = 0, pushSkip = 0;

        for (Usuario funcionario : funcionarios) {
            String funcNombre = funcionario.getNombre() != null ? funcionario.getNombre() : "Funcionario";
            String funcEmail = funcionario.getEmail();

            // EMAIL — llamada síncrona directa (NO @Async)
            try {
                if (funcEmail != null && !funcEmail.isBlank() && funcEmail.contains("@")) {
                    String html = sendGridEmailService.generarHtmlNotificacion(
                            funcNombre, nombreTramite, nombrePaso, false);
                    sendGridEmailService.enviarEmailSync(funcEmail, funcNombre, emailSubject, html);
                    emailOk++;
                    logger.info("  ✓ Email enviado a funcionario '{}' ({})", funcNombre, funcEmail);
                } else {
                    logger.warn("  ✗ Funcionario '{}' sin email válido. Omitiendo email.", funcNombre);
                    emailFail++;
                }
            } catch (Exception e) {
                emailFail++;
                logger.warn("  ✗ Error email a funcionario '{}' ({}): {}", funcNombre, funcEmail, e.getMessage());
            }

            // PUSH — llamada síncrona directa (NO @Async)
            try {
                List<String> tokens = funcionario.getFcmTokens();
                if (tokens != null && !tokens.isEmpty()) {
                    firebasePushService.enviarPushAUsuarioSync(tokens, pushTitle, pushBody);
                    pushOk++;
                    logger.info("  ✓ Push enviado a funcionario '{}' ({} tokens)", funcNombre, tokens.size());
                } else {
                    pushSkip++;
                }
            } catch (Exception e) {
                logger.warn("  ✗ Error push a funcionario '{}': {}", funcNombre, e.getMessage());
            }
        }

        logger.info("RESUMEN depto '{}': Emails OK={}, FAIL={} | Push OK={}, SKIP(sin token)={}",
                departamentoId, emailOk, emailFail, pushOk, pushSkip);
    }

    private void notificarCliente(Tramite tramite, String nombreTramite, String nombrePaso) {
        if (tramite.getClienteId() == null || tramite.getClienteId().isBlank()) {
            logger.warn("Trámite '{}' sin clienteId. No se puede notificar al cliente.", tramite.getId());
            return;
        }

        Usuario cliente = usuarioRepository.findById(tramite.getClienteId()).orElse(null);

        if (cliente == null) {
            logger.warn("Cliente '{}' no encontrado en BD para trámite '{}'. No se puede notificar.",
                    tramite.getClienteId(), tramite.getId());
            return;
        }

        String clienteNombre = cliente.getNombre() != null ? cliente.getNombre() : "Cliente";
        String clienteEmail = cliente.getEmail();

        String pushTitle = nombreTramite + " requiere su atención";
        String pushBody = "Necesitamos que complete información para el paso \"" + nombrePaso + "\" de su trámite.";
        String emailSubject = "Acción requerida — " + nombreTramite;

        logger.info("Notificando CLIENTE '{}' ({}) para paso '{}'...", clienteNombre, clienteEmail, nombrePaso);

        // EMAIL — síncrono
        try {
            if (clienteEmail != null && !clienteEmail.isBlank() && clienteEmail.contains("@")) {
                String html = sendGridEmailService.generarHtmlNotificacion(
                        clienteNombre, nombreTramite, nombrePaso, true);
                sendGridEmailService.enviarEmailSync(clienteEmail, clienteNombre, emailSubject, html);
                logger.info("  ✓ Email enviado a cliente '{}' ({})", clienteNombre, clienteEmail);
            } else {
                logger.warn("  ✗ Cliente sin email válido: '{}'", clienteEmail);
            }
        } catch (Exception e) {
            logger.warn("  ✗ Error email a cliente '{}' ({}): {}", clienteNombre, clienteEmail, e.getMessage());
        }

        // PUSH — síncrono
        try {
            List<String> tokens = cliente.getFcmTokens();
            if (tokens != null && !tokens.isEmpty()) {
                firebasePushService.enviarPushAUsuarioSync(tokens, pushTitle, pushBody);
                logger.info("  ✓ Push enviado a cliente '{}' ({} tokens)", clienteNombre, tokens.size());
            } else {
                logger.info("  - Cliente sin tokens push. Solo email.");
            }
        } catch (Exception e) {
            logger.warn("  ✗ Error push a cliente '{}': {}", clienteNombre, e.getMessage());
        }
    }
}
