package backendworkflow.backend.tramite.service;

import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.tramite.repository.TramiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlaEscalationService {

    private static final Logger logger = LoggerFactory.getLogger(SlaEscalationService.class);

    private final TramiteRepository tramiteRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SlaEscalationService(TramiteRepository tramiteRepository, SimpMessagingTemplate messagingTemplate) {
        this.tramiteRepository = tramiteRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Cron Job que corre cada hora en producción.
     */
    @Scheduled(fixedRate = 3600000)
    public void evaluateSlasScheduled() {
        logger.info("Iniciando evaluación automática de SLAs (Cron Job)...");
        evaluateAndEscalate(false);
    }

    /**
     * Evalúa los trámites y escala si es necesario.
     * @param force Para la defensa universitaria: si es true, usa un límite de tiempo mínimo (1 minuto).
     * @return Lista de trámites escalados.
     */
    public List<Tramite> evaluateAndEscalate(boolean force) {
        List<Tramite> pendientes = tramiteRepository.findByEstadoGlobal("PENDIENTE");
        List<Tramite> escalados = new ArrayList<>();

        for (Tramite tramite : pendientes) {
            if ("ALTA".equals(tramite.getPrioridad()) && Boolean.TRUE.equals(tramite.getRiesgoDemora())) {
                continue; // Ya está escalado al máximo
            }

            LocalDateTime creacion = tramite.getFechaCreacion() != null ? tramite.getFechaCreacion() : LocalDateTime.now();
            long minutosTranscurridos = ChronoUnit.MINUTES.between(creacion, LocalDateTime.now());
            
            long limiteMinutos = force ? 1 : (tramite.getTiempoEstimadoDias() != null ? (long) (tramite.getTiempoEstimadoDias() * 24 * 60) : 1440);

            if (minutosTranscurridos >= limiteMinutos) {
                logger.warn("Trámite {} excedió el SLA ({} mins). Escalando prioridad a ALTA.", tramite.getId(), minutosTranscurridos);
                tramite.setPrioridad("ALTA");
                tramite.setRiesgoDemora(true);
                tramiteRepository.save(tramite);
                escalados.add(tramite);

                messagingTemplate.convertAndSend("/topic/tramites", 
                    "{\"type\":\"SLA_ESCALATION\", \"tramiteId\":\"" + tramite.getId() + "\", \"prioridad\":\"ALTA\"}");
            } else if (minutosTranscurridos >= limiteMinutos * 0.75 && "BAJA".equals(tramite.getPrioridad())) {
                logger.warn("Trámite {} acercándose al SLA (75%). Escalando prioridad a MEDIA.", tramite.getId());
                tramite.setPrioridad("MEDIA");
                tramiteRepository.save(tramite);
                escalados.add(tramite);
                
                messagingTemplate.convertAndSend("/topic/tramites", 
                    "{\"type\":\"SLA_ESCALATION\", \"tramiteId\":\"" + tramite.getId() + "\", \"prioridad\":\"MEDIA\"}");
            }
        }

        if (!escalados.isEmpty()) {
            logger.info("Evaluación completada. Se escalaron {} trámites.", escalados.size());
        }
        return escalados;
    }

    public java.util.Map<String, Object> evaluateSlaManual() {
        List<Tramite> pendientes = tramiteRepository.findByEstadoGlobal("PENDIENTE");
        List<Tramite> escalados = evaluateAndEscalate(false);

        String message;
        if (!escalados.isEmpty()) {
            message = "¡Evaluación completada! Se detectaron y escalaron " + escalados.size() + " trámites que excedieron su tiempo límite.";
        } else {
            // Analizar por qué no se escaló nada
            long totalRevisados = pendientes.size();
            if (totalRevisados == 0) {
                message = "No hay trámites pendientes (en progreso) para analizar.";
            } else {
                Tramite masCercano = null;
                long menorFaltante = Long.MAX_VALUE;
                long diasEstancado = 0;
                double tiempoPredicho = 0;

                for (Tramite t : pendientes) {
                    if ("ALTA".equals(t.getPrioridad()) && Boolean.TRUE.equals(t.getRiesgoDemora())) continue;
                    LocalDateTime creacion = t.getFechaCreacion() != null ? t.getFechaCreacion() : LocalDateTime.now();
                    long minsTranscurridos = ChronoUnit.MINUTES.between(creacion, LocalDateTime.now());
                    long limiteMins = t.getTiempoEstimadoDias() != null ? (long) (t.getTiempoEstimadoDias() * 24 * 60) : 1440;
                    long faltante = limiteMins - minsTranscurridos;

                    if (faltante > 0 && faltante < menorFaltante) {
                        menorFaltante = faltante;
                        masCercano = t;
                        diasEstancado = minsTranscurridos / (24 * 60);
                        tiempoPredicho = t.getTiempoEstimadoDias() != null ? t.getTiempoEstimadoDias() : 1.0;
                    }
                }

                if (masCercano != null) {
                    double horasFaltantes = menorFaltante / 60.0;
                    String formatoFaltante = horasFaltantes > 24 ? String.format("%.1f días", horasFaltantes / 24.0) : String.format("%.1f horas", horasFaltantes);
                    
                    message = String.format("Se analizaron %d trámites en curso. " + 
                        "El sistema está al día. El trámite más próximo a vencer es '%s', que lleva %d días estancado, " +
                        "pero la IA le otorgó %.1f días de margen. Aún le faltan %s para cambiar de prioridad.", 
                        totalRevisados, masCercano.getNombrePlantilla(), diasEstancado, tiempoPredicho, formatoFaltante);
                } else {
                    message = "Se analizaron " + totalRevisados + " trámites, pero todos los que aplicaban ya están en prioridad ALTA o no tienen riesgo.";
                }
            }
        }

        return java.util.Map.of(
            "message", message,
            "escalatedCount", escalados.size(),
            "tramites", escalados
        );
    }

    public List<Tramite> getEscalatedTramites() {
        return tramiteRepository.findByEstadoGlobal("PENDIENTE").stream()
                .filter(t -> "ALTA".equals(t.getPrioridad()) && Boolean.TRUE.equals(t.getRiesgoDemora()))
                .toList();
    }

    public List<Tramite> getAnomalousTramites() {
        return tramiteRepository.findByEstadoGlobal("PENDIENTE").stream()
                .filter(t -> Boolean.TRUE.equals(t.getEsAnomalo()))
                .toList();
    }
}
