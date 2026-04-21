package backendworkflow.backend.services;

import backendworkflow.backend.models.*;
import backendworkflow.backend.repositories.DepartamentoRepository;
import backendworkflow.backend.repositories.PlantillaWorkflowRepository;
import backendworkflow.backend.repositories.TramiteAnalyticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AnalyticsService {

    private static final String SIN_DEPARTAMENTO = "SIN_DEPARTAMENTO";
    private static final String CLIENTE = "CLIENTE";

    private final TramiteAnalyticsRepository tramiteAnalyticsRepository;
    private final PlantillaWorkflowRepository plantillaWorkflowRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ClaudeAiService claudeAiService;
    private final ObjectMapper objectMapper;

    public AnalyticsService(
            TramiteAnalyticsRepository tramiteAnalyticsRepository,
            PlantillaWorkflowRepository plantillaWorkflowRepository,
            DepartamentoRepository departamentoRepository,
            ClaudeAiService claudeAiService
    ) {
        this.tramiteAnalyticsRepository = tramiteAnalyticsRepository;
        this.plantillaWorkflowRepository = plantillaWorkflowRepository;
        this.departamentoRepository = departamentoRepository;
        this.claudeAiService = claudeAiService;
        this.objectMapper = new ObjectMapper();
    }

    public AnalisisCuellosBotellaResponse analizarCuellosBotella(double horasEsperadasPromedio) {
        List<Document> aggregated = tramiteAnalyticsRepository.aggregateCompletedTramiteStageLogs();
        List<TramiteTiempoInterno> tramites = toInternalLogs(aggregated);

        List<TramiteTiempoCompacto> logsCompactos = tramites.stream()
                .map(TramiteTiempoInterno::toCompact)
                .toList();

        List<DepartamentoMetrica> metricasDepartamentos = calcularMetricasPorDepartamento(tramites);

        if (logsCompactos.isEmpty()) {
            return new AnalisisCuellosBotellaResponse(logsCompactos, metricasDepartamentos, List.of(), List.of());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("horasEsperadasPromedio", horasEsperadasPromedio);
        payload.put("logsCompactos", logsCompactos);
        payload.put("metricasDepartamentos", metricasDepartamentos);

        List<InsightAlerta> insights = List.of();
        List<PlanAccionItem> planAccion = List.of();

        try {
            String jsonInput = objectMapper.writeValueAsString(payload);
            String aiResponse = claudeAiService.analizarLogsTramites(jsonInput, horasEsperadasPromedio);
            ClaudeAnalyticsResult result = objectMapper.readValue(aiResponse, ClaudeAnalyticsResult.class);

            if (result.insights() != null) {
                insights = result.insights();
            }
            if (result.planAccion() != null) {
                planAccion = result.planAccion();
            }
        } catch (Exception ignored) {
            // Si Claude falla, devolvemos métricas crudas igualmente.
        }

        return new AnalisisCuellosBotellaResponse(logsCompactos, metricasDepartamentos, insights, planAccion);
    }

    private List<TramiteTiempoInterno> toInternalLogs(List<Document> aggregated) {
        List<TramiteTiempoInterno> results = new ArrayList<>();

        for (Document tramiteDoc : aggregated) {
            String tramiteId = asString(tramiteDoc.get("tramiteId"));
            String tipoTramite = asString(tramiteDoc.get("tipoTramite"));
            String plantillaId = asString(tramiteDoc.get("plantillaId"));

            @SuppressWarnings("unchecked")
            List<Document> etapasDoc = (List<Document>) tramiteDoc.get("etapas");
            if (etapasDoc == null || etapasDoc.isEmpty()) {
                continue;
            }

            List<EtapaInterna> etapas = new ArrayList<>();
            long totalMinutos = 0;

            for (Document etapaDoc : etapasDoc) {
                String pasoId = asString(etapaDoc.get("pasoId"));
                String funcionarioId = asString(etapaDoc.get("funcionarioId"));

                LocalDateTime entrada = toLocalDateTime(etapaDoc.get("fechaEntrada"));
                LocalDateTime salida = toLocalDateTime(etapaDoc.get("fechaSalida"));
                if (entrada == null || salida == null) {
                    continue;
                }

                long minutos = Math.max(0, ChronoUnit.MINUTES.between(entrada, salida));
                totalMinutos += minutos;
                etapas.add(new EtapaInterna(pasoId, funcionarioId, minutos));
            }

            if (!etapas.isEmpty()) {
                results.add(new TramiteTiempoInterno(tramiteId, tipoTramite, plantillaId, totalMinutos, etapas));
            }
        }

        return results;
    }

    private List<DepartamentoMetrica> calcularMetricasPorDepartamento(List<TramiteTiempoInterno> tramites) {
        Map<String, Map<String, String>> deptoPorPlantillaPaso = construirIndicePlantillaPaso();
        Map<String, String> nombresDeptos = departamentoRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Departamento::getId, Departamento::getNombre));

        Map<String, List<Long>> minutosPorDepto = new HashMap<>();

        for (TramiteTiempoInterno tramite : tramites) {
            Map<String, String> deptoPorPaso = deptoPorPlantillaPaso.getOrDefault(tramite.plantillaId(), Map.of());

            for (EtapaInterna etapa : tramite.etapas()) {
                String deptoId = resolveDepartamentoId(deptoPorPaso, etapa.pasoId());
                minutosPorDepto.computeIfAbsent(deptoId, key -> new ArrayList<>()).add(etapa.minutosEtapa());
            }
        }

        List<DepartamentoMetrica> metricas = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : minutosPorDepto.entrySet()) {
            String deptoId = entry.getKey();
            List<Long> muestras = entry.getValue();

            double promedioHoras = muestras.stream().mapToLong(Long::longValue).average().orElse(0.0) / 60.0;
            String nombre;
            if (CLIENTE.equals(deptoId)) {
                nombre = "Cliente";
            } else if (SIN_DEPARTAMENTO.equals(deptoId)) {
                nombre = "Sin Departamento";
            } else {
                nombre = nombresDeptos.getOrDefault(deptoId, "Departamento " + deptoId);
            }

            metricas.add(new DepartamentoMetrica(
                    deptoId,
                    nombre,
                    redondearDosDecimales(promedioHoras),
                    muestras.size()
            ));
        }

        metricas.sort(Comparator.comparingDouble(DepartamentoMetrica::promedioHoras).reversed());
        return metricas;
    }

    private Map<String, Map<String, String>> construirIndicePlantillaPaso() {
        Map<String, Map<String, String>> index = new HashMap<>();

        for (PlantillaWorkflow plantilla : plantillaWorkflowRepository.findAll()) {
            Map<String, String> pasoDepto = new HashMap<>();
            if (plantilla.getPasos() != null) {
                for (PasoWorkflow paso : plantilla.getPasos()) {
                    pasoDepto.put(paso.id(), normalizarDepartamentoId(paso.departamentoId()));
                }
            }
            index.put(plantilla.getId(), pasoDepto);
        }

        return index;
    }

    private String resolveDepartamentoId(Map<String, String> deptoPorPaso, String pasoId) {
        if (deptoPorPaso.containsKey(pasoId)) {
            return normalizarDepartamentoId(deptoPorPaso.get(pasoId));
        }
        return SIN_DEPARTAMENTO;
    }

    private String normalizarDepartamentoId(String departamentoId) {
        if (departamentoId == null || departamentoId.isBlank()) {
            return CLIENTE;
        }
        return departamentoId;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }

        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        if (value instanceof String text) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double redondearDosDecimales(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record EtapaInterna(String pasoId, String funcionarioId, long minutosEtapa) {
        EtapaTiempoCompacto toCompact() {
            return new EtapaTiempoCompacto(pasoId, funcionarioId, minutosEtapa);
        }
    }

    private record TramiteTiempoInterno(
            String tramiteId,
            String tipoTramite,
            String plantillaId,
            long minutosTotales,
            List<EtapaInterna> etapas
    ) {
        TramiteTiempoCompacto toCompact() {
            return new TramiteTiempoCompacto(
                    tramiteId,
                    tipoTramite,
                    minutosTotales,
                    etapas.stream().map(EtapaInterna::toCompact).toList()
            );
        }
    }

    private record ClaudeAnalyticsResult(
            List<InsightAlerta> insights,
            List<PlanAccionItem> planAccion
    ) {
    }
}
