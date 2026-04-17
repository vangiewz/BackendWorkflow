package backendworkflow.backend.services;

import backendworkflow.backend.models.Departamento;
import backendworkflow.backend.repositories.DepartamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClaudeAiService {

    private final DepartamentoRepository departamentoRepository;
    private final RestTemplate restTemplate;

    @Value("${claude.api.key}")
    private String claudeApiKey;

    public ClaudeAiService(DepartamentoRepository departamentoRepository) {
        this.departamentoRepository = departamentoRepository;
        this.restTemplate = new RestTemplate();
    }

    public String generarWorkflow(String politicaNegocio) {
        List<Departamento> departamentos = departamentoRepository.findAll();
        
        String stringDeDepartamentosBD = departamentos.stream()
                .map(d -> d.getNombre() + " (ID: " + d.getId() + ")")
                .collect(Collectors.joining(", "));

        String systemPrompt = """
                Eres un experto analista en BPM (Business Process Management). Tu objetivo es analizar una política de negocio proporcionada por el usuario y generar un flujo de trabajo secuencial estructurado en formato JSON.
                
                Sigue estrictamente estas directrices:
                
                <reglas>
                1. DEPARTAMENTOS: Utiliza únicamente los departamentos de esta lista: [{DEPARTAMENTOS}]. Selecciona solo los estrictamente necesarios para el trámite.
                2. ROL DEL CLIENTE: El "Cliente" ya no participa en el array de "pasos". El sistema asume que el trámite lo inicia automáticamente el Cliente rellenando el "formularioCliente". Ignora cualquier paso introductorio o final de recolección/notificación para el Cliente.
                3. ESPECIFICACIÓN DEL ACTOR: En la propiedad 'departamentoId' de cada paso, utiliza EXACTAMENTE el ID proporcionado junto a los departamentos seleccionados de la lista.
                4. FORMULARIOS REQUERIDOS: 
                   - Formula un JSON Schema inicial bajo la llave 'formularioCliente' que contenga todos los datos que el usuario debe proporcionar para detonar el flujo.
                   - Para CADA paso ("pasos"), incluye su propio 'formularioJson' requerido para la aprobación/gestión interna en ese momento particular.
                5. METADATOS Y TARIFACIÓN: Proporciona un título corto en 'nombreTramite', una breve descripción en 'descripcionTramite', una 'categoria' ("INTERNO" o "EXTERNO") y un 'costoBase' numérico. REGLA ESTRICTA: Si la politica de negocio es para algo interno o la categoría se evalúa como "INTERNO", el costoBase debe ser obligatoriamente 0.
                6. FORMATO ESTRICTO: Tu respuesta debe ser EXCLUSIVAMENTE un objeto JSON válido. No incluyas markdown (nada de ```json).
                </reglas>
                
                <estructura_json_esperada>
                {
                  "nombreTramite": "Nombre corto y profesional del trámite",
                  "descripcionTramite": "Descripción del alcance del trámite...",
                  "categoria": "EXTERNO",
                  "costoBase": 150.0,
                  "formularioCliente": {
                    "type": "object",
                    "properties": {
                      "ejemploDatoInicial": {
                        "type": "string",
                        "description": "Descripción clara del campo inicial"
                      }
                    },
                    "required": ["ejemploDatoInicial"]
                  },
                  "pasos": [
                    {
                      "orden": 1,
                      "departamentoId": "ID_DEL_DEPARTAMENTO_REAL",
                      "nombrePaso": "Revisión Documental Interna",
                      "formularioJson": {
                        "type": "object",
                        "properties": {
                           "aprobado": { "type": "boolean" }
                        }
                      }
                    }
                  ]
                }
                </estructura_json_esperada>
                """.replace("{DEPARTAMENTOS}", stringDeDepartamentosBD);

        Map<String, Object> requestBody = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 2500,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", politicaNegocio)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", claudeApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.set("content-type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.anthropic.com/v1/messages",
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getBody() != null && response.getBody().containsKey("content")) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getBody().get("content");
            if (!contentList.isEmpty()) {
                return (String) contentList.get(0).get("text");
            }
        }

        throw new RuntimeException("Error al comunicarse con Claude AI");
    }
}
