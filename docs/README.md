# Documentación

| Archivo | Qué es | Cuándo se toca |
|---|---|---|
| [`api-endpoints.md`](api-endpoints.md) | Referencia de la API: request, response y errores de cada endpoint | Con cada cambio de contrato |
| [`ideas.md`](ideas.md) | Bandeja de entrada: ideas y correcciones sin procesar | Cuando se te ocurre algo |
| [`roadmap.md`](roadmap.md) | Deuda técnica y funcionalidades propuestas, con el enfoque que hoy parece razonable | Cuando algo de `ideas.md` se decide postergar, o cuando se salda deuda |
| [`historico/`](historico/) | Documentos cerrados que el `CHANGELOG` referencia. No describen el sistema actual | Nunca |

Fuera de `docs/`:

| Archivo | Qué es |
|---|---|
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | Cómo está construido el sistema y **por qué** quedó así: módulos, seguridad, modelo de datos, concurrencia |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Qué cambió en cada versión, con los cambios incompatibles y las migraciones |
| [`../script/database/`](../script/database/) | Esquema, seeds y migraciones numeradas |

## Dónde buscar

- **"¿Cómo llamo a este endpoint?"** → `api-endpoints.md`
- **"¿Por qué esto funciona así?"** → `ARCHITECTURE.md`, y si no está, el javadoc del servicio:
  las decisiones no obvias están comentadas donde viven
- **"¿Qué rompe al actualizar?"** → `CHANGELOG.md`, sección *Cambios que rompen compatibilidad*
- **"¿Qué falta hacer?"** → `roadmap.md`
- **"¿Por qué el código tiene esta cicatriz?"** → `historico/0.4.0-correccion-de-bugs.md`
