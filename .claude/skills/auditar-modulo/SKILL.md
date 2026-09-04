---
name: auditar-modulo
description: Auditar un módulo de este backend buscando bugs reales, y arreglarlos siguiendo las convenciones del repo. Usar cuando pidan "revisá el módulo X", "buscá bugs en X", "auditá X" o al tocar un módulo entero.
---

# Auditar un módulo

Cada módulo vive en `src/main/java/.../modules/<nombre>/` y repite la misma estructura:
`entity/`, `repository/`, `api/` (interfaz + DTOs), `service/`, `controller/`. Todos comparten
las mismas convenciones y, por lo mismo, los mismos vicios: la mayor parte de la auditoría es
recorrer el checklist de abajo. Vale para cualquier módulo, incluidos los que todavía no
existen — empezar por `ls src/main/java/.../modules/` y no dar por sabido qué hay.

## Flujo

1. **Leer el módulo entero de una**, no de a archivos sueltos:
   `for f in $(find <ruta-del-modulo> -type f | sort); do echo "== $f"; cat -n "$f"; done`
2. **Mirar afuera**, que es donde aparecen la mitad de los bugs:
   - Esquema: la tabla en `script/database/00_init.sql` (índices, FKs, columnas NULLables).
   - Consumidores: `grep -rn "<Nombre>Api\|<Nombre>Repository" --include="*.java" src/main`
     fuera del módulo. Un servicio que se traga un `ResourceNotFoundException` suele estar
     ocultando un dato, no manejando un error.
   - `security/SecurityConfig.java`: qué exige la ruta del módulo para cada método HTTP. Hay
     reglas por método, así que un endpoint nuevo puede quedar cubierto —o desprotegido— sin
     que se note.
   - `shared/exeption/GlobalExceptionHandler.java`: qué excepciones caen en el catch-all y
     salen como 500 siendo errores del cliente.
3. **Reportar primero y esperar**. Lista ordenada por severidad, cada hallazgo con
   `archivo.java:línea`, qué se rompe y en qué caso concreto. Separar "bugs" de "menores /
   deuda". Si algo es una decisión de negocio (bloquear una baja, exigir unicidad de un
   nombre), preguntarlo en vez de elegir por cuenta propia.
4. **Arreglar** solo lo aprobado, con tests que ejerciten cada arreglo.
5. **Verificar** (ver más abajo) y recién ahí reportar como hecho.

## Checklist de fallos recurrentes

Cada punto salió de un bug real de este repo. Buscarlos todos, en ese orden.

1. **Soft delete salteado en algún camino.** La convención es `deleted_at IS NULL` en todas las
   consultas. Buscar `findById(` pelado: alcanza con que un solo método lo use para que una
   fila ya dada de baja siga siendo editable y responda 200.
2. **PUT que saltea los nulos.** Si el `update` hace `if (request.getX() != null) set(x)`, los
   campos opcionales no se pueden vaciar nunca: el pedido se acepta con 200 y el valor viejo
   queda. Un PUT es reemplazo total; asignar siempre.
3. **Paginación.** `PageRequest.of(page, size)` tira `IllegalArgumentException` con `size=0` o
   `page` negativo, y eso sale 500. Van `@Min`/`@Max` con tope de `size`. Y el `Sort` necesita
   desempate (`.and(Sort.by("id"))`): ordenar por un solo campo, más si es mutable como
   `updatedAt`, hace que las filas se repitan o se salteen entre páginas.
4. **N+1 en el `toResponse`.** Si arma el DTO llamando a otra API por fila, una página de 20 son
   41 consultas y un reporte que pide la tabla entera, cientos. Resolver una vez por id
   distinto, cacheando también los nulos (`containsKey`, no `computeIfAbsent`).
5. **Unicidad vs. baja lógica.** Los dos sentidos son bug:
   - Sin índice único, el chequeo del servicio es un TOCTOU: dos altas simultáneas pasan, y
     desde ahí una consulta que devuelve una entidad rompe con
     `IncorrectResultSizeDataAccessException`, o sea 500 permanente.
   - Con índice único sin filtrar por `deleted_at`, la fila borrada retiene el valor para
     siempre: el servicio lo da por libre y el INSERT choca con un 409 inentendible.
   La solución en los dos casos es el índice único sobre una columna generada (ver abajo).
6. **Excepciones fuera de las del proyecto.** Un `RuntimeException` pelado sale 500. Van
   `ResourceNotFoundException` (404), `BadRequestException` (400), `ForbiddenException` (403),
   `UnauthorizedException` (401).
7. **Hijos huérfanos al dar de baja, y bajas que borran datos en silencio.** Preguntar quién
   apunta a esa fila (por FK en el esquema y por id suelto en el código). O se dan de baja con
   el mismo timestamp y en la misma transacción, o la baja se rechaza con 400 si hay algo vivo
   colgando. Cuál de las dos corresponde es decisión de negocio: preguntarla.
8. **Filtrado en memoria.** `findAll().stream().filter(x -> x.getDeletedAt() == null)` levanta
   toda la tabla, borradas incluidas, e ignora el índice de `deleted_at`. Va una derivada
   `findAllByDeletedAtIsNull()`.
9. **Validación floja en el DTO.** `@Email` faltante, `@Size` que no coincide con la columna,
   texto sin recortar (`" Nombre"` y `"Nombre"` conviven porque la colación de MySQL 8 no
   ignora los espacios).
10. **Errores del cliente que salen 500.** Todo lo que llegue al `@ExceptionHandler(Exception)`
    del `GlobalExceptionHandler` siendo culpa del request es un bug.

## Convenciones al arreglar

**Ciclos de beans.** La inyección es por constructor (`@AllArgsConstructor`), así que un ciclo
entre servicios no arranca: revienta al levantar el contexto. Si el módulo A ya depende de
`BApi`, entonces B **no** puede depender de `AApi`; hay que inyectar el `ARepository` y dejarlo
comentado en el campo para que nadie lo "corrija" después. Antes de agregar una dependencia,
`grep` de quién depende de quién.

**Migraciones.** `spring.jpa.hibernate.ddl-auto=none`: el esquema es de los scripts. Cada cambio
va en un `script/database/NN_descripcion.sql` nuevo (seguir la numeración) **y** en los dos
`00_init*.sql`, para que una instalación limpia quede igual. El script abre con un comentario
que explica el porqué, sigue con una consulta informativa de los datos que podrían frenar el
`ALTER`, y cierra con un `SELECT` de verificación contra `information_schema`.

**Unicidad con baja lógica**, el patrón del repo — buscar los índices `uk_*_activo` existentes
como referencia:

```sql
ALTER TABLE t
    ADD COLUMN campo_activo <tipo>
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, campo, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_t_campo_activo (campo_activo);
```

Los NULL no chocan entre sí en un índice único de MySQL, así que la unicidad rige solo entre las
filas activas y la baja libera el valor. Si el valor **no** debe liberarse, el índice va sobre la
columna directa: es una decisión de negocio, no un detalle técnico.

**Baja lógica reversible.** Cuando la baja tiene que poder revertirse, copiar la convención de
donde ya esté implementada (`grep -rn "restaurar" src/main`): `deletedAt` expuesto en el response
(null salvo baja), `GET /v1?incluirBajas=true` para listarlas, `POST /v1/{id}/restaurar` sobre la
fila original —no un alta nueva, que partiría el historial—, y un `getByIdIncluyendoBajas` para
que el historial siga mostrando la referencia. Las validaciones de operación siguen usando
`existsById`, que ignora las bajas.

**Comentarios.** En español y explicando el *porqué*, sobre todo el bug que el código evita
("Con findById a secas una fila ya borrada seguía siendo editable"). No narrar lo que el código
ya dice.

**Docs.** Si cambia el contrato (params nuevos, códigos de error nuevos, campos en un DTO,
semántica de un verbo), actualizar `docs/api-endpoints.md`.

## Tests

No hay base de datos disponible, así que todo es Mockito y MockMvc standalone.

- **Servicio**: `@ExtendWith(MockitoExtension.class)` + `@Mock` de los repositorios +
  `@InjectMocks`. Strict stubs: no stubear lo que no se usa.
- **Controller** (para probar códigos HTTP sin contexto de Spring):
  `MockMvcBuilders.standaloneSetup(new XController(api)).setControllerAdvice(new GlobalExceptionHandler()).build()`.
- Trampa: `new PageImpl<>(List.of())` y `Page.empty()` sin pageable quedan `Unpaged`, y
  serializarlos tira `UnsupportedOperationException` (500 en el test). Usar
  `Page.empty(PageRequest.of(p, s))`.

## Verificar

```bash
./mvnw -q -o compile
./mvnw -o test -Dtest='<Clase>Test'   # lo que se tocó
./mvnw -o test                        # la suite entera
```

`MinimarketApplicationTests.contextLoads` **falla siempre** en este entorno: no hay datasource y
Hibernate no puede determinar el dialecto. No es tuyo. Si aparece cualquier otro fallo, antes de
atribuirlo confirmar con `git stash` que no venía de antes. Reportar el resultado con los números
reales ("110 tests, 109 pasan") y decir explícitamente cuál es el preexistente.

## No reportar como bugs

Son decisiones ya tomadas o deuda conocida; mencionarlas como menores a lo sumo, sin insistir:

- POST que devuelve 200 en vez de 201: es consistente en todo el proyecto, cambiarlo es global.
- `float` para dinero en vez de `BigDecimal`: deuda conocida, atraviesa todo el circuito de
  plata.
- Listados sin paginar cuando la tabla es chica por naturaleza.
- Métodos muertos en los repositorios: se limpian de paso, no son un hallazgo.
