# Plan de acción — Corrección de bugs del MVP

> Relevamiento del 2026-08-18. Todos los bugs marcados **[verificado]** fueron reproducidos
> contra la API real (MySQL 8.0 en Docker + `00_init.sql` + `01_seed.sql` + la app corriendo).
> El resto surge de lectura de código.

---

## Estado

**Fase 1 aplicada y verificada el 2026-08-18** (B-01, B-02, B-08, B-09). Se adelantó de la
Fase 2 la parte mínima de B-04 —el rol del JWT como authority + `@EnableMethodSecurity`— porque
sin eso el alta restringida a ADMIN no sería realmente restringida, y de la Fase 5 el handler
de `AccessDeniedException`, porque al empezar a denegar `@PreAuthorize` la denegación salía
como 500.

**Fase 2 aplicada y verificada el 2026-08-18** (B-03, B-04, B-07). Se adelantó también de la
Fase 5 el `authenticationEntryPoint` que devuelve 401 sin token, porque con roles activos el
front necesita distinguir "reautenticar" (401) de "no tenés permiso" (403).

**Fase 3 aplicada y verificada el 2026-08-18** (B-05, B-06, B-14, B-15, B-18, B-19). Cayó de
paso B-22, porque las `RuntimeException` crudas estaban en los mismos métodos reescritos.
Requiere migración de esquema: `script/database/02_migracion_fase3.sql` (o recrear con
`00_init.sql`, que ya la incluye).

Se adelantó de la Fase 4 un pedazo de B-10: el `saldoEsperado` pasó a calcularse como
Σ entradas − Σ salidas en vez de sumar las categorías una por una. Sin ese cambio las reversas
de caja (origen nuevo `REVERSA`) quedaban fuera del arqueo.

**Fase 4 aplicada y verificada el 2026-08-18** (B-10, B-11, B-12, B-13, B-21). Cayeron de paso
B-24 (la anotación de `DetalleVenta.idProducto`, que ya estaba alineada en el esquema) y el
logging del handler genérico de B-16 —adelantado a la fuerza: un 500 en el reporte de ganancias
no dejaba ningún rastro y era imposible diagnosticarlo sin eso—. Requiere migración de esquema:
`script/database/03_migracion_fase4.sql`.

**Fase 5 aplicada y verificada el 2026-08-18** (B-16, B-17, B-20, B-23, B-25). Requiere
migración de esquema: `script/database/04_migracion_fase5.sql` (renombre de `refresh_token`).

**Los 25 bugs del relevamiento están cerrados.** Queda pendiente el trabajo transversal de más
abajo: tests automatizados, Flyway y paginación de los listados.

## Resumen

| # | Bug | Severidad | Fase | Esfuerzo |
|---|---|---|---|---|
| B-01 | ~~El logout no cierra la sesión, siempre devuelve 400~~ ✅ | Crítico | 1 | S |
| B-02 | ~~Reset de contraseña rompe con 500 y hace rollback~~ ✅ | Crítico | 1 | S |
| B-03 | ~~Identidad falsificable vía header `idUsuario`~~ ✅ | Crítico | 2 | M |
| B-04 | ~~No hay control de roles (un empleado borra al admin)~~ ✅ | Crítico | 2 | M |
| B-05 | ~~Anular venta/compra no revierte stock ni caja~~ ✅ | Crítico | 3 | L |
| B-06 | ~~Se puede cobrar contra una sesión de caja cerrada~~ ✅ | Crítico | 3 | M |
| B-07 | ~~Usuario borrado sigue autenticado~~ ✅ | Alto | 2 | S |
| B-08 | ~~`register` entrega tokens sin verificar el email~~ ✅ | Alto | 1 | S |
| B-09 | ~~`jwt.secret` vacío ⇒ secreto aleatorio por arranque~~ ✅ | Alto | 1 | S |
| B-10 | ~~El resumen de caja ignora el parámetro `fecha`~~ ✅ | Alto | 4 | M |
| B-11 | ~~El historial de cortes reporta todo en 0~~ ✅ | Alto | 4 | M |
| B-12 | ~~El reporte de inventario ignora los lotes~~ ✅ | Alto | 4 | S |
| B-13 | ~~Los reportes se contradicen entre sí~~ ✅ | Alto | 4 | M |
| B-14 | ~~Stock inexistente ⇒ 500 por NPE~~ ✅ | Medio | 3 | S |
| B-15 | ~~Se pueden crear dos filas de stock por producto~~ ✅ | Medio | 3 | S |
| B-16 | ~~404 devuelve 500, falta 401, no se loguea nada~~ ✅ | Medio | 5 | S |
| B-17 | ~~`POST /ventas` devuelve `fecha: null`~~ ✅ | Medio | 5 | S |
| B-18 | ~~Carrera al abrir sesión de caja~~ ✅ | Medio | 3 | S |
| B-19 | ~~Faltan validaciones de cantidades y montos > 0~~ ✅ | Medio | 3 | S |
| B-20 | ~~Lotes de compra con `estado` NULL; GET que escribe~~ ✅ | Medio | 5 | M |
| B-21 | ~~Doble conteo en los bordes de los rangos de fecha~~ ✅ | Medio | 4 | S |
| B-22 | ~~`RuntimeException` cruda ⇒ 500 en vez de 400~~ ✅ | Bajo | 5 | S |
| B-23 | ~~N+1 en listados de ventas y compras~~ ✅ | Bajo | 5 | M |
| B-24 | ~~`DetalleVenta.idProducto` anotado `nullable=false` pero se guarda null~~ ✅ | Bajo | 5 | S |
| B-25 | ~~`RefreshToken` sin `@Table` ⇒ tabla `refresh_token` en singular~~ ✅ | Bajo | 5 | S |

Esfuerzo: **S** ≤ 1h · **M** 1–4h · **L** > 4h.

**Orden recomendado:** Fase 1 → 2 → 3 → 4 → 5. Las fases 1 y 2 son requisito para
salir a producción; la 3 evita descuadres de plata y stock; la 4 arregla lo que el
dueño del negocio va a mirar todos los días; la 5 es higiene.

---

## Fase 1 — Autenticación (bloqueante)

### B-01 · El logout no cierra la sesión [verificado]

**Dónde:** `AuthController.java:39-44` · `AuthApi.java:19` · `AuthService.java:108`

**Causa:** el controller lee el header `Authorization`, que trae el **access JWT**, y se lo
pasa a `revokeRefreshToken()`, que espera el **refresh token**. El SHA-256 del JWT no coincide
con ningún hash guardado ⇒ 400 y la sesión queda viva.

**Solución:** recibir el refresh token por body, igual que `/refresh`, y hacer la revocación
idempotente (un logout repetido no debería ser un error).

```java
// AuthController
@PostMapping("/v1/logout")
public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authApi.logout(request.getRefreshToken());
    return ResponseEntity.noContent().build();
}
```

```java
// TokenService.revokeRefreshToken — sin orElseThrow
refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashToken(rawToken))
    .ifPresent(rt -> {
        rt.setRevokedAt(LocalDateTime.now());
        rt.setActive(false);
        refreshTokenRepository.save(rt);
    });
```

**Verificar:** logout ⇒ 204; `refresh_token.is_active = 0`; `/refresh` con ese token ⇒ 400.

> Nota: el access JWT sigue siendo válido hasta que expire (24 h). Es el comportamiento
> normal de JWT, pero conviene bajar `jwt.expiration-hours` a 1–2 h y apoyarse en el refresh.

---

### B-02 · El reset de contraseña rompe con 500 y revierte todo [verificado]

**Dónde:** `TokenService.java:107-115` · `RefreshTokenRepository.java:14-15`

**Causa:** `findByUserIdAndIsActiveTrue` devuelve `Optional`, pero cada login crea un refresh
token nuevo sin revocar los anteriores. Con 2+ sesiones activas Spring Data tira
`IncorrectResultSizeDataAccessException`; como `confirmPasswordReset` es `@Transactional`,
hace rollback: la contraseña **no** cambia y el token de reset queda sin usar.

**Solución:** revocar en lote.

```java
// RefreshTokenRepository
// flushAutomatically es imprescindible: confirmPasswordReset deja cambios pendientes sobre
// usuario y auth_token que el clear posterior descartaría silenciosamente.
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("""
    UPDATE RefreshToken r
       SET r.isActive = false, r.revokedAt = :ahora
     WHERE r.userId = :userId AND r.isActive = true
    """)
int revokeAllByUserId(@Param("userId") UUID userId, @Param("ahora") LocalDateTime ahora);
```

```java
// TokenService
@Transactional
public void revokeAllUserRefreshTokens(UUID userId) {
    refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
}
```

Cambiar también `findByUserIdAndIsActiveTrue` a `List<RefreshToken>` si se lo quiere seguir
usando en otro lado. Mismo patrón de riesgo en `AuthTokensRepository.findByUserIdAndTokenTypeAndUsedFalse`
(hoy sin uso, pero es una bomba equivalente): dejarlo como `List` o borrarlo.

**Verificar:** loguear 4 veces, pedir reset, confirmar ⇒ 200, la contraseña nueva funciona y
las 4 sesiones quedan revocadas.

---

### B-08 · `register` entrega tokens sin verificar el email

**Dónde:** `AuthService.java:52-59`

**Causa:** se crea el usuario con `enabled=false` pero se le devuelven access y refresh token,
salteando la verificación que `login` sí exige. Además el token de verificación se genera y se
**descarta** (línea 56), y el proyecto no tiene envío de mails ⇒ por la vía normal nadie puede
activarse nunca.

**Solución (MVP):** `register` no devuelve tokens, devuelve 201 con el usuario creado.

```java
public UsuarioResponse register(RegisterRequest request) {
    ...
    var rawToken = tokenService.generateVerificationToken(user.getId());
    log.info("Token de verificación para {}: {}", user.getEmail(), rawToken); // temporal
    return toUserResponse(user);
}
```

**Decisión tomada (2026-08-18):** el alta de usuarios la hace **solo el ADMIN**. No hay
autorregistro público, así que el circuito de verificación por email queda fuera del MVP.

**Solución definitiva:**

- Se elimina el **endpoint** `POST /api/auth/v1/register`.
- `AuthApi.register(RegisterRequest)` **se conserva implementada pero sin exponer**, lista para
  el día que se habilite el autorregistro: crea el usuario con `enabled=false` y emite el token
  de verificación. Para activarla hacen falta tres cosas, anotadas en su javadoc: exponerla en
  `AuthController`, agregar el envío de mail y permitir la ruta en `SecurityConfig`.
- Se agrega `POST /api/users/v1`, restringido a ADMIN, que crea el usuario con `enabled=true`
  y el `rol` indicado en el request (por defecto `EMPLEADO`).
- `AuthToken` / `TokenType.VERIFICATION` quedan en el código para el reseteo de contraseña y
  para el autorregistro futuro.

El envío de mails (verificación y reseteo autogestionado) queda anotado en
`proximos-pasos-propuesta.md`.

> Nota: el enum `Rol` solo tiene `ADMIN` y `EMPLEADO`. Si más adelante hace falta un rol
> `CLIENTE`, es un cambio aparte (nuevo valor del enum + `ALTER TABLE usuarios MODIFY rol ENUM(...)`
> + su propia matriz de permisos).

---

### B-09 · `jwt.secret` vacío genera un secreto aleatorio por arranque

**Dónde:** `JwtProvider.java:31-37` · `.env.example`

**Causa:** si `jwt.secret` está en blanco se genera una clave random en `@PostConstruct`. Cada
reinicio invalida todas las sesiones y con más de una instancia los tokens no son
intercambiables.

**Solución:** fallar el arranque si falta el secreto.

```java
@PostConstruct
public void init() {
    if (secret == null || secret.isBlank()) {
        throw new IllegalStateException("jwt.secret es obligatorio (variable de entorno JWT_SECRET)");
    }
    secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
}
```

Y documentar en `.env.example` cómo generarlo:

```bash
# JWT_SECRET: 32+ bytes en base64
openssl rand -base64 48
```

---

## Fase 2 — Autorización e identidad

### B-03 · Identidad falsificable vía header `idUsuario` [verificado]

**Dónde:** `VentaController.java:40,70` · `CompraController.java:34` · `CajaController.java:37,49,56` ·
`CorteController.java:30` · `InventarioController.java:62` · `ProductoController.java:35`

**Causa:** el usuario sale de un header que el cliente controla, no del JWT. Es un resto del
diseño previo a JWT. Probado: un EMPLEADO, con su propio token, registró una venta que quedó
asentada a nombre del ADMIN.

**Solución:** eliminar todos los `@RequestHeader ... idUsuario` y tomar la identidad de
`SecurityUtils.getCurrentUserId()`.

```java
@PostMapping("/v1")
public ResponseEntity<VentaResponse> realizarVenta(@Valid @RequestBody VentaRequest request) {
    return ResponseEntity.ok(ventasApi.realizarVenta(SecurityUtils.getCurrentUserId(), request));
}
```

De paso, que `SecurityUtils` tire `UnauthorizedException` en vez de `IllegalStateException`,
que hoy termina en 500:

```java
throw new UnauthorizedException("Usuario no autenticado");
```

**Impacto en el front:** hay que sacar el header `idUsuario` de todas las llamadas. Actualizar
`docs/api-endpoints.md` en el mismo commit.

---

### B-04 · No hay control de roles [verificado]

**Dónde:** `SecurityConfig.java:19-37` · `JwtAuthenticationFilter.java:34`

**Causa:** el filtro lee el claim `rol` y lo descarta, autenticando con authorities vacías. No
hay `@EnableMethodSecurity` ni un solo `@PreAuthorize`. Probado: un EMPLEADO borró al ADMIN
(`DELETE /api/users/v1/{id}` ⇒ 204). `PATCH /users/{id}` tampoco valida propiedad.

**Solución en tres pasos:**

1. Propagar el rol como authority:

```java
var authorities = rol != null
    ? List.of(new SimpleGrantedAuthority("ROLE_" + rol))
    : List.<SimpleGrantedAuthority>of();
var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
```

2. Habilitar seguridad por método: `@EnableMethodSecurity` sobre `SecurityConfig`.

3. Definir la matriz de permisos. Propuesta mínima:

| Recurso | ADMIN | EMPLEADO |
|---|---|---|
| `/api/users/**` (salvo `/me`, `/change-password`) | sí | no |
| `/api/productos`, `/categorias`, `/proveedores` (escritura) | sí | no |
| `/api/reportes/**` | sí | no |
| `/api/caja/v1/corte` | sí | no |
| ventas, cobros, compras, movimientos de caja, inventario | sí | sí |

Se puede resolver casi todo en `SecurityConfig` con `requestMatchers(...).hasRole("ADMIN")`,
y usar `@PreAuthorize` solo donde hace falta lógica de propiedad:

```java
// UsuarioController
@PatchMapping("/v1/{id}")
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
```

Aplicar lo mismo a `DELETE /users/{id}` (solo ADMIN) y `change-password` (dueño o ADMIN).

**Cómo quedó (aplicado):** la matriz vive en `SecurityConfig` para lo que se resuelve por URL
y en `@PreAuthorize` solo donde depende del dueño del recurso (`/users/{id}`). `change-password`
terminó siendo **solo del dueño**, ni siquiera del ADMIN: requiere la contraseña actual, así que
para un tercero corresponde el flujo de reseteo.

> **Trampa que costó encontrar:** las denegaciones por URL devolvían `401` en vez de `403`.
> El `AccessDeniedHandler` respondía bien con 403, pero el contenedor hacía un forward a
> `/error` para renderizar el cuerpo; ese forward entra de nuevo al filter chain, esta vez como
> anónimo, se deniega y el `authenticationEntryPoint` pisa la respuesta con 401. Se resuelve
> agregando `/error` a `permitAll()`. Las denegaciones de `@PreAuthorize` no sufrían esto porque
> las atiende el `@RestControllerAdvice` dentro del dispatch normal.

**Verificar:** EMPLEADO ⇒ 403 en usuarios, reportes, corte, escritura de catálogo y anulaciones;
200 en vender, cobrar, caja e inventario. Sin token ⇒ 401.

---

### B-07 · Usuario borrado sigue autenticado [verificado]

**Dónde:** `JwtAuthenticationFilter.java:30-43` · `UsuarioService.java:70-74`

**Causa:** el filtro no consulta la base y el borrado lógico no revoca refresh tokens. Probado:
con el admin soft-deleted, su JWT siguió devolviendo 200.

**Solución:**

1. En `UsuarioService.delete`, revocar las sesiones: `tokenService.revokeAllUserRefreshTokens(id)`
   (usa el bulk de B-02).
2. En el filtro, verificar que el usuario siga activo y habilitado antes de autenticar.

```java
if (!usuarioApi.existById(UUID.fromString(userId))) {
    SecurityContextHolder.clearContext();
    filterChain.doFilter(request, response);
    return;
}
```

Es un `SELECT` por request; con `jwt.expiration-hours` bajo se podría evitar, pero para el
volumen de un minimarket el costo es irrelevante y cierra el agujero.

**Cómo quedó (aplicado):** `UsuarioApi.puedeOperar(id)` (activo + habilitado) lo consulta el
filtro en cada request, y `UsuarioService.delete` llama a `AuthApi.revokeAllSessions(id)`.
Para inyectar `UsuarioApi` en el filtro hubo que mover el bean `PasswordEncoder` de
`SecurityConfig` a `config/PasswordEncoderConfig`: si no, se formaba un ciclo
`SecurityConfig → filtro → UsuarioService → AuthService → PasswordEncoder → SecurityConfig`.

---

## Fase 3 — Integridad de plata y stock

### B-05 · Anular venta o compra no revierte nada [verificado]

**Dónde:** `VentaService.java:207-219` · `CompraService.java:150-162`

**Causa:** el `delete` solo marca `deleted_at`. Probado: stock 48 → vendo 1 → 47 → anulo la
venta → sigue en 47, sin movimiento de reversa y sin devolver la plata a la caja.

**Solución (regla simple para el MVP):**

1. **Prohibir anular una venta ya cobrada.** Si está cobrada, exigir un flujo explícito de
   devolución (fuera de alcance del MVP; anotarlo en `proximos-pasos-propuesta.md`).

```java
if (Boolean.TRUE.equals(venta.getCobrada())) {
    throw new BadRequestException("No se puede anular una venta cobrada; usar devolución");
}
```

2. **Revertir el stock** de la venta no cobrada, generando movimientos de reversa (nunca
   borrando los originales, para no perder la trazabilidad):

```java
for (DetalleVenta d : detalles) {
    if (d.getIdProducto() == null) continue;                 // ítem MANUAL: no toca stock
    inventarioApi.aumentar(MovimientoStockRequest.builder()
        .idProducto(d.getIdProducto())
        .cantidad(d.getCantidad())
        .tipo("AJUSTE")
        .motivo("Reversa por anulación de venta " + venta.getId())
        .idUsuario(idUsuario)
        .build());
}
```

3. Para productos con lotes, devolver la cantidad a los lotes de los que se descontó. Hoy
   `MovimientoStock` ya guarda `id_lote`, así que la reversa se puede reconstruir leyendo los
   movimientos de tipo VENTA de esa venta — **pero falta el vínculo movimiento → venta**.
   Agregar `id_referencia` a `MovimientoStock` (igual que en `MovimientoCaja`) y usarlo:

```sql
ALTER TABLE movimientos_stock
  ADD COLUMN id_referencia BINARY(16) NULL AFTER id_usuario,
  ADD KEY ix_mov_stock_referencia (id_referencia);
```

4. Lo mismo del lado de compras: al anular, descontar lo ingresado y dar de baja el lote
   creado; si la compra generó una salida de caja, rechazar la anulación si la sesión ya cerró.

**Cómo quedó (aplicado):** la reversa se arma sobre los **movimientos** que referencian el
comprobante, no sobre sus detalles. Es lo que permite reponer cada lote en la cantidad exacta
de la que salió cuando el FIFO repartió una línea entre varios lotes (verificado: una venta de
30 que tomó 12 de un lote y 18 de otro se revierte devolviendo 12 y 18 a los lotes correctos).
Para eso `Venta` y `Compra` se persisten **antes** de tocar el stock, así cada movimiento nace
con su `id_referencia`.

Guardas que quedaron activas:

- Venta cobrada ⇒ 400 (corresponde devolución, no anulación).
- Compra cuya mercadería ya se vendió ⇒ 400, sin dejar la anulación a medias.
- Compra pagada por caja en un turno ya cerrado, o sin turno abierto ⇒ 400.

Los movimientos originales nunca se borran: la reversa es un movimiento nuevo de tipo `AJUSTE`
con el mismo `id_referencia`.

**Verificar:** vender 1, anular, comprobar que el stock vuelve a 48 y que existe un movimiento
de reversa.

---

### B-06 · Se puede cobrar contra una sesión de caja cerrada [verificado]

**Dónde:** `VentaService.java:240-243` · `CompraService.java:105-107`

**Causa:** se usa el `idSesion` que el cliente mandó al crear la venta, sin validar que sea la
sesión abierta. Probado: hice el corte y después cobré la venta ⇒ se insertó una ENTRADA de
$1900 en una sesión **CERRADA** con arqueo ya firmado. Esa plata no aparece en ningún corte.

**Solución:** que el `idSesion` no venga del cliente. Al cobrar, resolver la sesión activa en
el momento del cobro.

```java
// CajaApi — método nuevo
UUID getIdSesionActiva();   // lanza BadRequestException si no hay ninguna abierta
```

```java
// VentaService.cobrar
if ("EFECTIVO".equals(request.getMetodoPago())) {
    UUID idSesion = cajaApi.getIdSesionActiva();
    venta.setIdSesion(idSesion);
    cajaApi.registrarEntradaAutomatica(idSesion, idUsuario, venta.getTotal(), "VENTA", venta.getId());
}
```

**Decisión tomada (2026-08-18):** solo el efectivo impacta en la caja. Hoy una venta con
tarjeta también suma al arqueo, lo que hace que el conteo físico nunca cierre. La venta con
tarjeta **se sigue registrando como tal** (`metodo_pago`, `cobrada`, `fecha_cobro`) y aparece
en los reportes de ventas y en el desglose por medio de pago; lo único que no genera es
`MovimientoCaja`. En consecuencia, el arqueo (`saldoEsperado`) pasa a reflejar únicamente el
efectivo, que es lo que el cajero cuenta al cierre, y `ResumenCajaResponse` debería exponer
también el total no-efectivo del turno para que el corte sea legible.

Aplicar lo mismo en `CompraService.crear` (línea 105) para la salida de caja.

**Cómo quedó (aplicado):** `CajaApi.getIdSesionActiva()` resuelve el turno en el momento del
cobro y el `idSesion` desapareció de `VentaRequest` y `CompraRequest`. En compras lo reemplaza
un booleano `pagoEnEfectivo`, que expresa la intención sin dejar que el cliente elija el turno.
El método de pago se normaliza y valida contra `EFECTIVO | TARJETA | TRANSFERENCIA`, y el
vuelto solo se calcula en efectivo.

Verificado: venta en efectivo ⇒ entra a la caja; misma venta con tarjeta ⇒ queda cobrada y
registrada con su `metodo_pago`, pero no genera movimiento y el `saldoEsperado` no se mueve;
cobrar en efectivo con la caja cerrada ⇒ 400 y ningún movimiento se cuela en el turno cerrado.

> Pendiente para la Fase 4: el corte no muestra el total cobrado con tarjeta/transferencia del
> turno. No se puede resolver desde `CajaService` sin crear un ciclo con `VentasApi`
> (`VentaService` ya depende de `CajaApi`); sale naturalmente al persistir el desglose del
> corte (B-11) o componiéndolo desde el módulo de reportes.

---

### B-18 · Carrera al abrir sesión de caja

**Dónde:** `CajaService.java:38-51`

**Causa:** el chequeo de "¿hay una sesión abierta?" y el insert no son atómicos. Dos sesiones
ABIERTA rompen el módulo entero, porque todas las consultas usan `Optional` y pasan a tirar
`NonUniqueResultException`.

**Solución:** garantía a nivel base + consulta defensiva.

```sql
-- Una sola sesión ABIERTA a la vez (los NULL no colisionan en un UNIQUE de MySQL)
ALTER TABLE sesiones_caja
  ADD COLUMN sesion_abierta_uk VARCHAR(10)
      GENERATED ALWAYS AS (IF(estado = 'ABIERTA' AND deleted_at IS NULL, 'ABIERTA', NULL)) VIRTUAL,
  ADD UNIQUE KEY uk_sesiones_una_abierta (sesion_abierta_uk);
```

Y cambiar `findByEstadoAndDeletedAtIsNull` por
`findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc` (ya existe en el repositorio) en
`abrirSesion`, `getSesionActiva`, `obtenerSesionActiva` y `getMovimientos`, para que un dato
sucio degrade en vez de romper.

---

### B-14 · Stock inexistente ⇒ 500 por NPE [verificado]

**Dónde:** `InventarioService.java:47-50` y `:102-107`

**Causa:** `findByIdProductoAndDeletedAtIsNull` devuelve `null` y se lo desreferencia sin
control. Probado: pedir el stock de un producto sin fila ⇒ 500.

**Solución:** que el repositorio devuelva `Optional<Stock>` y decidir el comportamiento:

```java
public StockResponse getByIdProducto(UUID idProducto) {
    return stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto)
        .map(this::toStockResponse)
        .orElseGet(() -> StockResponse.builder().idProducto(idProducto).cantidad(0).build());
}
```

Devolver 0 es mejor que 404 acá, porque un producto recién creado todavía no tiene fila de
stock. En `delete` sí corresponde `ResourceNotFoundException`.

---

### B-15 · Se pueden crear dos filas de stock por producto

**Dónde:** `InventarioService.java:36-45`

**Causa:** `crear` no verifica si el producto ya tiene stock. Con dos filas,
`findByIdProductoAndDeletedAtIsNull` tira `NonUniqueResultException` de forma permanente.

**Solución:** validar en el servicio y reforzar en la base.

```java
if (stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto()).isPresent()) {
    throw new BadRequestException("El producto ya tiene stock inicializado");
}
```

```sql
ALTER TABLE stock
  ADD COLUMN producto_activo BINARY(16)
      GENERATED ALWAYS AS (IF(deleted_at IS NULL, id_producto, NULL)) VIRTUAL,
  ADD UNIQUE KEY uk_stock_producto_activo (producto_activo);
```

Mejor todavía: crear la fila de stock automáticamente al crear el producto, y sacar el endpoint
manual.

---

### B-19 · Faltan validaciones de cantidades y montos

**Dónde:** `CompraService.java:61` · `InventarioService.java:53,76` · `CajaService.java:62,77`

**Causa:** compras, movimientos de stock y movimientos de caja aceptan valores negativos.
`disminuir` con cantidad negativa **suma** stock.

**Solución:** validación declarativa en los DTOs, que ya pasan por `@Valid`:

```java
@Positive(message = "La cantidad debe ser mayor a 0")
private int cantidad;

@Positive(message = "El monto debe ser mayor a 0")
private float monto;

@PositiveOrZero
private float precioUnitario;
```

Y en `CompraService.crear`, rechazar `request.getDetalle()` nulo o vacío antes de iterar
(hoy es un NPE ⇒ 500), igual que ya hace `VentaService`.

---

## Fase 4 — Caja y reportes

### B-10 · El resumen de caja ignora el parámetro `fecha` [verificado]

**Dónde:** `CajaService.java:135-171`

**Causa:** recibe `fecha`, la devuelve en el JSON y calcula todo sobre la sesión abierta.
Probado: `?fecha=2020-01-01` devolvió los datos de la sesión de hoy. Además exige sesión
abierta, así que no se puede consultar un día ya cerrado.

**Solución:** separar dos operaciones que hoy están mezcladas.

- `GET /api/caja/v1/resumen/sesion` → resumen de la sesión abierta (lo que hace hoy).
- `GET /api/caja/v1/resumen/diario?fecha=` → agrega los movimientos del día por rango de fechas,
  sin depender de que haya sesión abierta:

```java
List<MovimientoCaja> movimientos = movimientoCajaRepository
    .findByCreatedAtBetweenAndDeletedAtIsNull(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay().minusNanos(1));
```

El `saldoInicial` del día sale de la sesión abierta ese día (o 0 si hubo varias; en ese caso
sumar los saldos iniciales de cada sesión del día).

**Cómo quedó (aplicado):** quedaron dos endpoints distintos, porque son dos preguntas distintas.
`GET /api/caja/v1/resumen/sesion` responde "cómo viene el turno abierto" y `GET
/api/caja/v1/resumen/diario?fecha=` responde "qué pasó tal día", sin depender de que haya una
caja abierta. El saldo inicial del día es la suma de los saldos iniciales de las sesiones
abiertas esa fecha. Verificado: consultar ayer devuelve vacío en vez de los datos de hoy.

---

### B-11 · El historial de cortes reporta todo en 0 [verificado]

**Dónde:** `CajaService.java:193-216` y `:246-253`

**Causa:** `getCorteById`, `getUltimoCorte` y `getHistorialCortes` pasan `resumen = null`, y
`toCorteResponse` fabrica un resumen vacío. El desglose nunca se persiste al cerrar. Probado:
un corte de una sesión con ventas devuelve `totalVentas: 0.0, cantidadVentas: 0`.

**Solución:** persistir el desglose en el corte, que es un documento contable y no se debe
recalcular.

```sql
ALTER TABLE sesiones_caja
  ADD COLUMN total_ventas             FLOAT NULL,
  ADD COLUMN cantidad_ventas          INT   NULL,
  ADD COLUMN total_compras            FLOAT NULL,
  ADD COLUMN cantidad_compras         INT   NULL,
  ADD COLUMN total_entradas_manuales  FLOAT NULL,
  ADD COLUMN total_salidas_manuales   FLOAT NULL;
```

En `realizarCorte`, guardar esos valores junto a `saldoEsperado`; en las consultas históricas,
reconstruir el `ResumenCajaResponse` desde la entidad en vez de inventarlo.

Alternativa sin migración: recalcular el resumen a partir de los movimientos de esa sesión
(`findByIdSesionAndDeletedAtIsNull`). Es más barato de implementar pero el corte pasa a ser
mutable — no recomendado para un documento de arqueo.

De paso, `getHistorialCortes` usa `findAll()` y filtra en memoria: reemplazar por una query
`findByEstadoAndDeletedAtIsNullOrderByFechaCierreDesc` con paginación.

**Cómo quedó (aplicado):** el desglose se congela al cerrar y el historial lo lee de la entidad.
Los cortes anteriores a este cambio devuelven los totales en **`null`**, no en 0: null es "no se
sabe", 0 se leería como "no hubo ventas". Por eso los campos del `ResumenCajaResponse` pasaron de
primitivos a objetos. El `findAll()` en memoria se reemplazó por la query ordenada por fecha de
cierre; falta paginarla si el historial crece.

---

### B-12 · El reporte de inventario ignora los lotes [verificado]

**Dónde:** `ReporteService.java:115-137`

**Causa:** solo mira la tabla `stock`. Probado: la leche, con 36 unidades en lotes, sale con
`stockActual = 0`.

**Solución:** elegir la fuente según `manejaLotes`.

```java
int stock = p.isManejaLotes()
    ? inventarioApi.getStockPorLotes(p.getId())   // SUM(cantidad) de lotes activos
    : inventarioApi.getByIdProducto(p.getId()).getCantidad();
```

Agregar en `LoteRepository`:

```java
@Query("SELECT COALESCE(SUM(l.cantidad), 0) FROM Lote l WHERE l.idProducto = :id AND l.deletedAt IS NULL")
int sumCantidadByProducto(@Param("id") UUID id);
```

Y sacar el `try/catch (Exception e) { stock = 0; }` de la línea 120, que hoy tapa el NPE de
B-14 y cualquier otro error.

---

### B-13 · Los reportes se contradicen entre sí

**Dónde:** `ReporteService.java:74-112` y `:140-170`

**Causa doble:**

1. `getReporteVentas` filtra `cobrada=true` (vía `getResumenDiario`), pero
   `getReporteGanancias` y `getProductosMasVendidos` usan `getByFecha`, que incluye ventas sin
   cobrar. Los dos reportes dan números distintos para el mismo período.
2. "Ganancia bruta" resta las **compras del período** en lugar del costo de lo vendido: una
   reposición grande da ganancia negativa aunque el negocio haya ganado plata.

**Solución:**

1. Agregar `getByFechaCobradas(desde, hasta)` en `VentasApi` y usarlo en los tres reportes,
   apoyado en `findByCreatedAtBetweenAndCobradaTrueAndDeletedAtIsNull`, que ya existe.
2. Calcular la ganancia como margen sobre lo vendido, guardando el **costo unitario al momento
   de la venta** en el detalle (hoy no se guarda):

```sql
ALTER TABLE detalles_ventas ADD COLUMN costo_unitario FLOAT NULL AFTER precio_unitario;
```

```
ganancia = Σ (precio_unitario − costo_unitario) × cantidad
```

**Cómo quedó (aplicado):** `detalles_ventas.costo_unitario` congela el costo al vender y la
ganancia es `Σ (precio − costo) × cantidad` sobre ventas **cobradas**, filtradas por
`fecha_cobro`. `totalCompras` sigue en la respuesta pero como dato informativo de flujo, fuera
del cálculo. Se agregó `unidadesSinCosto`: los ítems manuales y los productos sin costo cargado
no tienen costo conocido, y sin ese contador la ganancia se leería como más alta de lo que es.

Verificado: 8800 de venta con 6080 de costo ⇒ ganancia 2720; una compra de 180000 en el mismo
período deja la ganancia intacta (antes daba −171200). Los tres reportes que informan ventas
—`/reportes/ventas`, `/reportes/ganancias` y `/ventas/resumen/diario`— ahora devuelven el mismo
número para el mismo rango.

3. Consistencia de fecha: hoy el resumen diario filtra por `created_at` y no por `fecha_cobro`,
   así que una venta creada ayer y cobrada hoy suma en el día de ayer. Filtrar por `fecha_cobro`
   en todo lo que sea plata cobrada.

---

### B-21 · Doble conteo en los bordes de los rangos

**Dónde:** `VentaService.java:256-259` · `ReporteService.java:75-76,141-142`

**Causa:** `Between` es inclusivo en ambos extremos y el rango termina en
`hasta.plusDays(1).atStartOfDay()`, así que una venta a las 00:00:00.000000 exactas cuenta en
dos días.

**Solución:** cerrar el rango en `.minusNanos(1)`, o cambiar las queries a
`... >= :desde AND ... < :hasta`. Lo segundo es más limpio; hacerlo una sola vez en cada
repositorio y reusar.

---

## Fase 5 — Higiene

### B-16 · 404 devuelve 500, falta 401, no se loguea nada [verificado]

**Dónde:** `GlobalExceptionHandler.java:43-46` · `SecurityConfig.java:19-37`

**Causa:** `@ExceptionHandler(Exception.class)` se traga todo, incluidas las excepciones de
Spring. Probado: una URL inexistente devuelve **500** en vez de 404, y sin token la API
responde **403** en vez de 401. Nada se loguea, así que el 500 de B-02 no dejó rastro.

**Solución:**

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
    log.error("Error no controlado", ex);          // @Slf4j
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
}

@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
    return buildResponse(HttpStatus.NOT_FOUND, "Recurso no encontrado");
}

@ExceptionHandler(AccessDeniedException.class)   // deja pasar el 403 real de Spring Security
public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    return buildResponse(HttpStatus.FORBIDDEN, "No tiene permisos para esta operación");
}
```

Y en `SecurityConfig`, un `AuthenticationEntryPoint` que devuelva 401 cuando no hay token:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(
    (req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
```

Agregar además `DataIntegrityViolationException` ⇒ 409 y
`MethodArgumentTypeMismatchException` ⇒ 400 (hoy un UUID mal formado en la URL da 500).

**Cómo quedó (aplicado):** el handler genérico loguea con stacktrace y se sumaron cuatro
handlers específicos: `NoResourceFoundException` ⇒ 404, `MethodArgumentTypeMismatchException`
⇒ 400, `HttpMessageNotReadableException` ⇒ 400 y `DataIntegrityViolationException` ⇒ 409.

> El logging se adelantó durante la Fase 4 por necesidad: el reporte de ganancias tiraba 500 sin
> dejar rastro y no había forma de diagnosticarlo. Con el `log.error` puesto, el stacktrace
> apareció en el primer intento y delató una llamada a un método de repositorio renombrado.

---

### B-17 · `POST /ventas` devuelve `fecha: null` [verificado]

**Dónde:** `VentaService.java:154-161`

**Causa:** el DTO se arma dentro de la transacción, antes de que el `created_at` generado esté
disponible en la entidad. El `GET` posterior sí trae la fecha.

**Solución:** setear la fecha explícitamente al construir la respuesta, o forzar el flush.

```java
venta = ventaRepository.saveAndFlush(venta);
```

Mismo patrón a revisar en `CompraService.crear` y en los movimientos de caja, cuyo
`MovimientoCajaResponse.fecha` sale de `getCreatedAt()`.

**Cómo quedó (aplicado):** `saveAndFlush` en la creación de ventas, compras y los cuatro
métodos de movimientos de caja. Verificado: los tres endpoints devuelven la fecha real.

---

### B-20 · Lotes de compra con `estado` NULL y GET que escribe

**Dónde:** `CompraService.java:69-75` · `InventarioService.java:170-211`

**Causa:** el lote creado desde una compra se guarda sin `estado`; recién se completa cuando
alguien llama a `GET /lotes`, que además **escribe en la base dentro de un GET**
(`actualizarEstado`, sin `@Transactional`).

**Solución:** dejar de persistir un estado derivado. El `estado` es función de
`fecha_vencimiento` y la fecha de hoy, así que se calcula al leer:

```java
private LoteResponse toLoteResponse(Lote l, Map<UUID, String> nombres) {
    return LoteResponse.builder()
        ...
        .estado(calcularEstado(l.getFechaVencimiento()).name())   // sin tocar la base
        .build();
}
```

Con eso `actualizarEstado` desaparece, `getByEstado` filtra sobre el valor calculado y el
lote creado por compra deja de importar. Si se quiere conservar la columna para consultas SQL,
actualizarla con un job `@Scheduled` diario, nunca desde un GET.

En el mismo lugar, `EstadoLote.valueOf(estado.toUpperCase())` tira `IllegalArgumentException`
⇒ 500 para un estado inválido: validar y devolver 400.

**Cómo quedó (aplicado):** la regla se mudó a `EstadoLote.calcularPara(fechaVencimiento)`, así
no queda duplicada, y las respuestas la calculan al leer. `actualizarEstado` —el GET que
escribía— desapareció. La columna se sigue completando **al crear** el lote, incluidos los que
nacen de una compra (antes quedaban en NULL), para que las consultas SQL directas tengan un
valor razonable.

Verificado: dos GET seguidos de `/lotes` no modifican `updated_at`, y un lote cuya columna dice
`VIGENTE` pero vence en 5 días se informa como `PROXIMO`.

---

### B-22 · `RuntimeException` cruda ⇒ 500 en vez de 400

**Dónde:** `InventarioService.java:118` ("Stock invalido") y `:157` ("Fecha de vencimiento obligatoria")

**Solución:** reemplazar por `BadRequestException`. La validación de la fecha, además, va como
`@NotNull` en `LoteRequest`.

---

### B-23 · N+1 en listados

**Dónde:** `VentaService.java:176-203` · `CompraService.java:121-147` · `ReporteService.java:48-62`

**Causa:** un query de detalles por cada venta/compra; el reporte de ventas hace además un
query por día del rango.

**Solución:** traer los detalles en un solo golpe y agrupar en memoria.

```java
List<UUID> ids = ventas.stream().map(Venta::getId).toList();
Map<UUID, List<DetalleVenta>> porVenta = detalleVentaRepository
    .findByIdVentaInAndDeletedAtIsNull(ids)
    .stream().collect(Collectors.groupingBy(DetalleVenta::getIdVenta));
```

**Cómo quedó (aplicado):** los listados de ventas y compras arman las respuestas con dos
consultas fijas —una del comprobante y una de todos sus detalles con `IN (...)`— en vez de una
por comprobante. El reporte de ventas dejó de pedir el resumen día por día: trae el rango
completo de una y agrupa en memoria.

Verificado con `show-sql`: listar 7 ventas emite **1** consulta a `detalles_ventas` (antes 7), y
un reporte de 30 días emite **3** consultas en total (antes 30+).

> **Pendiente a propósito:** la paginación de `GET /ventas` y `GET /compras`, que hoy traen la
> tabla entera. Cambiar la respuesta de array a objeto paginado rompe el front en silencio, así
> que conviene coordinarlo, no colarlo dentro de una corrección de bugs.

---

### B-24 · `DetalleVenta.idProducto` anotado `nullable = false`

**Dónde:** `DetalleVenta.java:32-33` · `VentaService.java:138`

La entidad declara `@Column(name = "id_producto", nullable = false)` pero el servicio le asigna
`null` en los ítems MANUAL. El esquema de `00_init.sql` ya lo define NULL para que las ventas
manuales funcionen; falta alinear la anotación:

```java
@Column(name = "id_producto")
private UUID idProducto;
```

---

### B-25 · `RefreshToken` sin `@Table`

**Dónde:** `RefreshToken.java:14`

Es la única entidad sin `@Table`, así que la tabla queda como `refresh_token` (singular)
mientras el resto usa plural. Si se unifica, hay que renombrar también en `00_init.sql`:

```java
@Entity
@Table(name = "refresh_tokens")
```

```sql
RENAME TABLE refresh_token TO refresh_tokens;
```

**Cómo quedó (aplicado):** hecho, junto con el renombre de sus índices y la clave foránea. El
`AuthTokensRepository.findByUserIdAndTokenTypeAndUsedFalse` que devolvía `Optional` pasó a
`List`: era la misma bomba de B-02 esperando a que alguien pidiera dos veces el reseteo.

> Ojo al aplicar la migración: renombrar la tabla con la app corriendo invalida las sesiones
> abiertas. Parar la app antes, o avisar que hay que reloguear.

---

## Trabajo transversal

- **Tests:** hoy solo existe `MinimarketApplicationTests` (context load). Antes de tocar auth,
  escribir tests de integración con `@SpringBootTest` + MockMvc para: login → logout → refresh
  rechazado; reset de contraseña con varias sesiones; venta con ítem MANUAL; cobro con caja
  cerrada; anulación con reversa de stock. Son justo los casos que rompieron.
- **Logging:** no hay ni un `log` en el proyecto. Como mínimo, WARN en fallos de autenticación
  y ERROR con stacktrace en el handler genérico.
- **Migraciones:** varias correcciones tocan el esquema (B-05, B-11, B-13, B-15, B-18). Con la
  base ya en uso conviene sumar Flyway y convertir `00_init.sql` en `V1__init.sql`, en vez de
  seguir editando el script a mano.
- **Documentación:** actualizar `docs/api-endpoints.md` (se va el header `idUsuario`, cambia el
  contrato de `/logout` y de `/register`) y la sección "Modelo de Datos" de `ARCHITECTURE.md`,
  que sigue describiendo tablas en singular y `fecha_eliminacion`.

---

## Cómo reproducir y verificar

Entorno usado para confirmar los bugs, reutilizable como smoke test tras cada fase:

```bash
docker run -d --name mm-test -p 3399:3306 -e MYSQL_ROOT_PASSWORD=test123 mysql:8.0
docker exec -i mm-test mysql -uroot -ptest123 < script/database/00_init.sql
docker exec -i mm-test mysql -uroot -ptest123 < script/database/01_seed.sql

./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="\
--spring.datasource.url=jdbc:mysql://127.0.0.1:3399/minimarket?allowPublicKeyRetrieval=true&useSSL=false \
--spring.datasource.password=test123 \
--spring.jpa.hibernate.ddl-auto=validate"
```

Usuario del seed: `admin@minimarket.local` / `Admin123!`.
`ddl-auto=validate` verifica de paso que el esquema siga alineado con las entidades.
