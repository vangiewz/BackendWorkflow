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
                Eres un experto analista en BPM (Business Process Management). Tu objetivo es analizar una política de negocio proporcionada por el usuario y generar un flujo de trabajo estructurado como un Grafo Dirigido en formato JSON.
                
                Sigue estrictamente estas directrices:
                
                <reglas>
                1. DEPARTAMENTOS: Utiliza únicamente los departamentos de esta lista: [{DEPARTAMENTOS}]. 
                2. ROL DEL CLIENTE: Si un paso requiere intervención del Cliente (por ejemplo, corregir documentos, proveer más información tras un rechazo), asigna el valor `null` a 'departamentoId'. El sistema entenderá que ese paso le pertenece al Cliente.
                3. ESPECIFICACIÓN DEL ACTOR: En la propiedad 'departamentoId' de cada paso, utiliza EXACTAMENTE el ID proporcionado o `null` si es el Cliente.
                4. FORMULARIOS REQUERIDOS: 
                   - Formula un JSON Schema inicial bajo la llave 'formularioCliente' conteniendo los datos que detonan el flujo.
                   - Para CADA paso cuya 'tipo' sea "ACTIVIDAD", incluye un 'formularioJson' requerido. 
                   - Si el paso es de tipo "DECISION", 'formularioJson' debe ser null o vacío.
                   - TIPOS DE CAMPO SOPORTADOS en los formularios JSON Schema:
                     * Texto: { "type": "string", "description": "Etiqueta del campo" }
                     * Número entero: { "type": "integer", "description": "Etiqueta" }
                     * Booleano/Checkbox: { "type": "boolean", "description": "Etiqueta" }
                     * Fecha: { "type": "string", "format": "date", "description": "Etiqueta" }
                     * Fecha y Hora: { "type": "string", "format": "date-time", "description": "Etiqueta" }
                   - Usa "format": "date" para campos de fecha (ej. fecha de nacimiento, fecha de inicio) y "format": "date-time" para fecha con hora.
                5. ESTRUCTURA DE GRAFO (Nodos y Aristas):
                   - Sustituye el antiguo concepto lineal por nodos de grafo. Cada paso debe tener un 'id' único (ej: "paso_1").
                   - Propiedad 'tipo': Puede ser "ACTIVIDAD" o "DECISION".
                   - Propiedad 'siguientes': Es un mapa que define las aristas salientes. Para ACTIVIDADES lineales, usa {"default": "id_del_siguiente_paso"}. Para un rombo de DECISION, incluye múltiples opciones, ej: {"Aprobado": "paso_3", "Rechazado": "paso_1"}. Si no hay paso siguiente (fin del flujo), el mapa 'siguientes' debe estar vacío o no existir.
                6. METADATOS: Proporciona 'nombreTramite', 'descripcionTramite', 'categoria' ("INTERNO" o "EXTERNO") y 'costoBase' numérico. REGLA ESTRICTA: Si la categoria es "INTERNO", el costoBase debe ser 0.
                7. FORMATO ESTRICTO: Tu respuesta debe ser EXCLUSIVAMENTE un objeto JSON válido. No incluyas markdown.
                </reglas>
                
                <estructura_json_esperada>
                {
                  "nombreTramite": "Nombre corto y profesional del trámite",
                  "descripcionTramite": "Descripción del alcance...",
                  "categoria": "EXTERNO",
                  "costoBase": 150.0,
                  "formularioCliente": {
                    "type": "object",
                    "properties": {
                      "ejemploDatoInicial": { "type": "string", "description": "Un dato de texto" },
                      "fechaSolicitud": { "type": "string", "format": "date", "description": "Fecha de la solicitud" }
                    },
                    "required": ["ejemploDatoInicial", "fechaSolicitud"]
                  },
                  "pasos": [
                    {
                      "id": "paso_1",
                      "tipo": "ACTIVIDAD",
                      "departamentoId": "ID_DEL_DEPARTAMENTO_REAL",
                      "nombrePaso": "Revisión Documental Interna",
                      "formularioJson": {
                        "type": "object",
                        "properties": { "documentacionOK": { "type": "boolean" } }
                      },
                      "siguientes": { "default": "paso_2" }
                    },
                    {
                      "id": "paso_2",
                      "tipo": "DECISION",
                      "departamentoId": "ID_DEL_DEPARTAMENTO_REAL",
                      "nombrePaso": "¿Documentación Aprobada?",
                      "formularioJson": null,
                      "siguientes": {
                        "Aprobada": "paso_3",
                        "Rechazada": "paso_retroalimentacion_cliente"
                      }
                    },
                    {
                      "id": "paso_retroalimentacion_cliente",
                      "tipo": "ACTIVIDAD",
                      "departamentoId": null,
                      "nombrePaso": "Substraer Documentos Faltantes",
                      "formularioJson": {
                        "type": "object",
                        "properties": { "nuevoDocumento": { "type": "string" } }
                      },
                      "siguientes": { "default": "paso_1" }
                    },
                    {
                      "id": "paso_3",
                      "tipo": "ACTIVIDAD",
                      "departamentoId": "OTRO_ID_DEPARTAMENTO",
                      "nombrePaso": "Firma Final",
                      "formularioJson": {
                        "type": "object",
                        "properties": { "firma": { "type": "string" } }
                      },
                      "siguientes": {}
                    }
                  ]
                }
                </estructura_json_esperada>
                """.replace("{DEPARTAMENTOS}", stringDeDepartamentosBD);

        return sendToClaude(systemPrompt, politicaNegocio, 2500);
          }

          public String analizarLogsTramites(String logsCompactosJson, double horasEsperadasPromedio) {
        String systemPrompt = """
          Eres un consultor senior de procesos institucionales y productividad operativa.
          Analiza los logs de tiempos de tramites y devuelve SOLO JSON valido.

          Objetivo:
          1) Identificar departamentos que superen el promedio esperado de tiempo.
          2) Sugerir causa probable: falta de personal o complejidad del formulario.
          3) Construir un plan de accion priorizado.

          Regla de negocio sobre actores:
          - Cuando departamentoId sea "CLIENTE", ese tiempo corresponde al cliente y NO debe contarse como retraso del departamento interno.
          - Si observas que el cuello de botella principal está en pasos del cliente, menciónalo explícitamente como externo.

          Regla de severidad:
          - CRITICO: retraso >= 24h sobre el promedio esperado.
          - ADVERTENCIA: retraso >= 8h y < 24h sobre el promedio esperado.
          - INFO: casos por debajo de esos umbrales.

          Responde estrictamente en este schema JSON:
          {
            "insights": [
              {
                "severidad": "CRITICO | ADVERTENCIA | INFO",
                "titulo": "string",
                "descripcion": "string",
                "departamentoId": "string|null",
                "funcionarioId": "string|null",
                "retrasoHoras": 0.0,
                "causaProbable": "FALTA_PERSONAL | COMPLEJIDAD_FORMULARIO | MIXTO"
              }
            ],
            "planAccion": [
              {
                "prioridad": "ALTA | MEDIA | BAJA",
                "accion": "string",
                "objetivo": "string",
                "plazoHoras": "string"
              }
            ]
          }

          No incluyas markdown ni texto adicional. Usa el valor de horasEsperadasPromedio como referencia principal.
          """;

        String userPrompt = "horasEsperadasPromedio=" + horasEsperadasPromedio + "\n" + logsCompactosJson;
        return sendToClaude(systemPrompt, userPrompt, 1800);
    }

    public String sugerirCamposFormulario(String schemaJson, String textoUsuario, String modo) {
        String systemPrompt = """
          Eres un asistente para autocompletar formularios de tramites institucionales.
          Recibirás:
          1) El JSON Schema del formulario del paso activo.
          2) Un texto libre del usuario (chat o transcripcion de voz).

          Reglas estrictas:
          - Devuelve SOLO un JSON valido, sin markdown.
          - El JSON debe tener esta forma exacta:
            {
              "sugerencia": { "campo": valor },
              "observacion": "texto corto"
            }
          - Incluye exclusivamente campos existentes en properties del schema.
          - Si faltan datos, omite el campo en lugar de inventarlo.
          - Respeta tipos: string, integer/number, boolean, date/date-time.
          - Para enum, usa solo valores permitidos.
          - Si no hay datos utiles, devuelve sugerencia vacia.
          """;

        String userPrompt = "modo=" + modo + "\nSCHEMA:\n" + schemaJson + "\n\nTEXTO_USUARIO:\n" + textoUsuario;
        return sendToClaude(systemPrompt, userPrompt, 1200);
    }

    private String sendToClaude(String systemPrompt, String userContent, int maxTokens) {
        Map<String, Object> requestBody = Map.of(
          "model", "claude-haiku-4-5-20251001",
          "max_tokens", maxTokens,
          "system", systemPrompt,
          "messages", List.of(
            Map.of("role", "user", "content", userContent)
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
