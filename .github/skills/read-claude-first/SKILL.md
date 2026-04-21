---
name: read-claude-first
description: "Lee y aplica las reglas de CLAUDE.md antes de cada peticion. Usar para tareas de codigo, edicion de archivos, ejecucion de comandos y revisiones en repos con reglas locales."
argument-hint: "Ruta objetivo (opcional): backend, frontend, movil_workflow o auto"
user-invocable: true
---

# Read CLAUDE First

## Outcome

Aplicar de forma consistente las reglas de CLAUDE.md antes de ejecutar cualquier accion en la peticion del usuario.

## When To Use

- Peticiones de implementacion o cambios de codigo.
- Ejecucion de tests, build o comandos de terminal.
- Revisiones de codigo y refactors.
- Cualquier peticion en workspaces con uno o mas archivos CLAUDE.md.

## Procedure

1. Determinar el alcance de la peticion.

- Si la tarea apunta a un archivo/carpeta concreta, usar ese workspace como primario.
- Si la tarea cruza varios workspaces, tratar cada uno por separado.

2. Localizar los archivos CLAUDE.md aplicables.

- Buscar primero en el workspace objetivo.
- Si hay multiples niveles, priorizar el mas especifico para la ruta trabajada.
- Si la tarea cruza workspaces, leer el CLAUDE.md de cada workspace involucrado.

3. Extraer reglas operativas en checklist antes de actuar.

- Reglas de arquitectura y organizacion de carpetas.
- Reglas de estilo y patrones obligatorios.
- Restricciones de herramientas/comandos.
- Restricciones de seguridad, pruebas o validaciones.

4. Ejecutar la peticion cumpliendo el checklist.

- Antes de cada lote de herramientas, validar que la accion no viola reglas locales.
- Si una accion entra en conflicto con reglas, ajustar enfoque o pedir confirmacion al usuario.

5. Verificar cumplimiento al cerrar.

- Confirmar que el resultado final respeta las reglas relevantes de CLAUDE.md.
- Si no habia CLAUDE.md aplicable, indicarlo explicitamente.

## Decision Points

- No se encuentra CLAUDE.md:
  Continuar con politicas base del agente y avisar que no se encontraron reglas locales.

- Hay conflicto entre reglas:
  Aplicar precedencia por especificidad de ruta (mas especifico gana). Si persiste ambiguedad, preguntar al usuario.

- La tarea cruza repositorios:
  Aplicar reglas por repositorio para cada cambio/comando y documentar supuestos.

## Completion Checks

- Se leyo al menos un CLAUDE.md aplicable antes de editar o ejecutar comandos.
- Existe checklist de reglas activas para la peticion.
- No hay violaciones visibles a restricciones obligatorias del CLAUDE.md relevante.
- El cierre explica brevemente como se respeto el marco local.
