# Arquitectura — backend-minimarket v0.5.0

Backend de un punto de venta para minimarket: catálogo, ventas con cobro, compras a proveedores,
inventario con lotes, caja con arqueo y reportes.

## Stack Tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| Java | 21 | Lenguaje |
| Spring Boot | 4.1.1 | Framework principal |
| Spring Data JPA / Hibernate | 7.4 | ORM y persistencia |
| Spring Security | -- | Autenticación JWT y autorización por rol |
| JJWT | 0.12.6 | Firma y validación de tokens |
| SpringDoc OpenAPI | 3.1.1 | Documentación de API |
| MySQL | 8.x | Base de datos |
| Lombok | -- | Reducción de boilerplate |
| Maven | 3.9.14 | Build y dependencias |

## Estructura del Proyecto

El proyecto está organizado **por módulo de negocio**, no por capa técnica: cada módulo agrupa
su entidad, su repositorio, su servicio, su controlador y su contrato público.

```
src/main/java/com/SolucionesInformaticasBA/minimarket/
+-- MinimarketApplication.java     # Entry point
+-- config/                        # CorsConfig, PasswordEncoderConfig
+-- security/                      # SecurityConfig, JwtProvider, JwtAuthenticationFilter
+-- shared/                        # SecurityUtils, mail/EmailService, excepciones y handler
+-- modules/
    +-- auth/                      # Login, refresh, logout, invitaciones y reseteo
    +-- usuarios/                  # Alta y gestión de usuarios
    +-- categorias/                # Catálogo: categorías
    +-- proveedores/               # Catálogo: proveedores
    +-- productos/                 # Catálogo: productos
    +-- inventario/                # Stock, lotes y movimientos de stock
    +-- ventas/                    # Venta, cobro y anulación
    +-- compras/                   # Compra a proveedor y anulación
    +-- caja/                      # Sesiones, movimientos y corte
    +-- reportes/                  # Ventas, ganancias, inventario, más vendidos
```

Cada módulo repite la misma estructura interna:

```
modules/<modulo>/
+-- api/                # Contrato público del módulo
|   +-- <Modulo>Api.java    # Interfaz: lo único que otros módulos pueden usar
|   +-- dto/                # DTOs de entrada y salida
+-- controller/         # Endpoints REST
+-- service/            # Lógica de negocio (implementa <Modulo>Api)
+-- repository/         # Spring Data JPA
+-- entity/             # Entidades JPA
+-- enums/              # Enumeraciones propias del módulo
```

### Comunicación entre módulos

Un módulo nunca toca el repositorio ni la entidad de otro: se comunica a través de su interfaz
`XxxApi`. Eso mantiene los límites explícitos y deja el acoplamiento a la vista en el
constructor de cada servicio.

```
reportes  -> ventas, compras, inventario, productos
ventas    -> caja, inventario, productos, usuarios
compras   -> caja, inventario, productos, proveedores, usuarios
productos -> categorias, proveedores, usuarios
inventario-> productos, usuarios
usuarios  -> auth                      (revocar sesiones, y enviar la invitación al dar de alta)
auth, caja, categorias, proveedores    (sin dependencias salientes)
```

Las dependencias son acíclicas y conviene que siga así. Dos casos concretos que lo demuestran:

- `ventas -> caja` impide que `caja` consulte ventas. Por eso el desglose por medio de pago de
  un turno lo expone el módulo de ventas (`GET /api/ventas/v1/resumen/sesion/{id}`) y no el
  corte, que solo cuenta efectivo.
- El bean `PasswordEncoder` vive en `config/`, no en `SecurityConfig`: el filtro JWT consulta
  usuarios, y tenerlo en `SecurityConfig` cerraba un ciclo de beans en el arranque.

## Seguridad

Autenticación **stateless por JWT**. El login devuelve un access token (JWT firmado, con vigencia
de `JWT_EXPIRATION_HOURS`, 24 h por defecto) y un refresh token opaco de 30 días, del que solo se
guarda el SHA-256.

```
Login  -> access token (JWT) + refresh token
Request-> Authorization: Bearer <access token>
         JwtAuthenticationFilter valida firma y vigencia,
         busca en la base el rol vigente del usuario (vacío si está dado de
         baja o bloqueado) y publica ROLE_<ROL> más las authorities de los
         roles de menor jerarquía
Refresh-> rota el par (revoca el anterior)
Logout -> revoca el refresh token (idempotente)
```

- **Identidad:** siempre desde el JWT, vía `SecurityUtils.getCurrentUserId()`. Ningún endpoint
  acepta el id de usuario del cliente, **con una sola excepción documentada**: el endpoint de
  sincronización de tickets offline (ver *Ventas creadas sin conexión*). Ahí el vendedor de cada
  ticket viaja en el payload porque es un hecho de hace dos días, no de quien está llamando.
- **Roles:** `SUPERADMIN` > `ADMIN` > `EMPLEADO`, aplicados con `@EnableMethodSecurity`. Las
  reglas por URL están en `SecurityConfig`; `@PreAuthorize` se usa solo donde el permiso depende
  de quién es el dueño del recurso (`/api/users/{id}`).
- **Jerarquía:** el filtro JWT le da a cada usuario las authorities de su rol **y las de todos
  los de abajo**, así un `hasRole('ADMIN')` alcanza también al SUPERADMIN sin enumerar roles en
  cada regla. El rol sale de la base en cada request (`UsuarioApi.rolVigente`), no del claim del
  token: si saliera del claim, degradar a alguien no tendría efecto hasta que su JWT expirara. Los permisos que además dependen del rol del **objetivo** —dar de alta, bloquear
  o eliminar a otro usuario— se deciden en `UsuarioService`, que es donde ese rol se conoce: se
  exige mando estricto, de modo que un ADMIN no puede tocar a otro ADMIN ni al SUPERADMIN, y
  nadie se bloquea ni se borra a sí mismo.
- **SUPERADMIN:** llave maestra, no se crea por API (ningún rol manda sobre su propio nivel).
  Sale del seed de la base; ver `script/database/01_seed.sql`.
- **Contraseñas:** BCrypt.
- **Estado de cuenta:** `PENDIENTE` / `ACTIVO` / `BLOQUEADO`, independiente del borrado lógico.
  Solo un usuario `ACTIVO` puede autenticarse y operar.
- **Baja y bloqueo:** ambos revocan las sesiones del usuario y su JWT deja de servir en la
  request siguiente, porque el filtro consulta el estado en cada llamada. La diferencia es que
  el bloqueo es reversible y conserva la cuenta.
- **Restauración:** una baja también se revierte, con una invitación nueva. Siempre sobre la
  fila original: el id del usuario es permanente —lo referencian ventas, compras, movimientos
  de caja y de stock con `ON DELETE RESTRICT`—, así que un alta nueva con el mismo email
  partiría su historial en dos. El email y el username siguen reservados durante la baja,
  porque las unique keys no miran `deleted_at`; de ahí que restaurar nunca pueda colisionar, y
  que reusar el email de alguien dado de baja no sea posible sin restaurarlo.
- **Alta por invitación, la única que hay:** el administrador carga los datos, la cuenta nace
  `PENDIENTE` con una contraseña aleatoria que nadie conoce, y la persona define la suya desde
  el enlace que le llega por mail (token `INVITATION`, 72 h, de un solo uso). No hay
  autorregistro ni alta directa: quien invita nunca conoce la credencial del invitado. Si el
  mail no sale, el alta se revierte: no queda una cuenta muerta ocupando ese email.
- **Reseteo de contraseña sobre una cuenta pendiente:** además de cambiar la contraseña, la
  activa. El token del reseteo llega al email de la cuenta, que es la misma prueba de identidad
  que pide la invitación; si no la activara, quien lo usa en vez de aceptar la invitación
  quedaría con una contraseña válida y sin poder entrar. Por lo mismo, la invitación deja de
  valer apenas la cuenta sale de `PENDIENTE`.
- **Configuración obligatoria:** `JWT_SECRET`. Sin él la app no arranca.
- **Mails** (`shared/mail/EmailService`): invitaciones y reseteo de contraseña. **Sin
  `MAIL_HOST` no se manda nada** — el mail queda en el log con el enlace incluido, que es como
  se trabaja en desarrollo sin un SMTP. Variables: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
  `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME` y `FRONTEND_URL` (la base de los enlaces).
- **CSRF** deshabilitado (API stateless); **CORS** configurable por `CORS_ALLOWED_ORIGINS`,
  con GET, POST, PUT, PATCH, DELETE y OPTIONS habilitados.

> El access token no se puede revocar antes de que expire: el logout invalida el refresh token,
> pero el JWT ya emitido sigue siendo válido hasta su vencimiento. Con 24 h por defecto la
> ventana es amplia; conviene bajarla a 1–2 h y apoyarse en el refresh. Lo que sí tiene efecto
> inmediato, porque el filtro lo consulta en cada request, es la baja, el bloqueo y el cambio de
> rol: el token viejo deja de servir, o pasa a otorgar los permisos del rol nuevo.

| Operación | SUPERADMIN | ADMIN | EMPLEADO |
|---|:---:|:---:|:---:|
| Vender, cobrar, comprar, mover stock y caja | ✅ | ✅ | ✅ |
| Consultar catálogo · ver y editar su propio usuario | ✅ | ✅ | ✅ |
| Escritura de catálogo · anular ventas y compras · corte de caja · reportes | ✅ | ✅ | ❌ |
| Ajuste de inventario contra conteo físico (`/controlar`, `/lotes/ajustar`) | ✅ | ✅ | ❌ |
| Alta, bloqueo y baja de EMPLEADO | ✅ | ✅ | ❌ |
| Alta, bloqueo y baja de ADMIN | ✅ | ❌ | ❌ |

## Modelo de Datos

17 tablas. Convenciones transversales: PK `UUID` (`BINARY(16)`), borrado lógico con `deleted_at`
(NULL = activo) y auditoría `created_at` / `updated_at`.

> En `ventas` y `detalles_ventas`, `created_at` y `deleted_at` **no** son auditoría de la fila:
> significan cuándo ocurrió el ticket y cuándo se anuló, y en una venta offline las manda el
> dispositivo. Ver *Ventas creadas sin conexión*.

| Tabla | Propósito |
|---|---|
| `usuarios` | Usuarios del sistema (SUPERADMIN / ADMIN / EMPLEADO), con estado de cuenta |
| `auth_tokens` | Tokens de un solo uso: invitación y reseteo de contraseña |
| `refresh_tokens` | Sesiones activas (hash del refresh token) |
| `categorias` | Categorías de producto |
| `proveedores` | Proveedores |
| `productos` | Catálogo. El stock **no** vive acá |
| `producto_proveedor` | Precio de referencia de cada proveedor por producto (catálogo de consulta) |
| `stock` | Existencias agregadas por producto (una fila activa; los que manejan lotes no llevan) |
| `lote` | Lotes con vencimiento, para productos con `maneja_lotes` |
| `movimientos_stock` | Kardex: toda entrada y salida, con `id_referencia` al comprobante |
| `ventas` | Cabecera de venta |
| `detalles_ventas` | Líneas de venta, con precio y **costo congelados** al vender |
| `compras` | Cabecera de compra |
| `detalles_compras` | Líneas de compra |
| `sesiones_caja` | Turnos de caja y su corte, con el desglose congelado al cerrar |
| `movimientos_caja` | Movimientos de efectivo de cada turno |
| `anulaciones_pendientes` | Anulaciones que llegaron antes que el ticket que anulan. Sin FK a `ventas`, a propósito |

### Relaciones

No hay relaciones JPA (`@ManyToOne`): los vínculos son campos `UUID` y las claves foráneas se
declaran en el esquema (`script/database/00_init.sql`), con `ON DELETE RESTRICT`, seguro porque
todos los borrados de la aplicación son lógicos.

```
Usuario --+-- Venta / Compra / MovimientoStock / MovimientoCaja
          +-- SesionCaja (apertura y cierre)
          +-- AuthToken / RefreshToken (ON DELETE CASCADE)

Producto --+-- Stock            (1 fila activa, solo si NO maneja lotes)
           +-- Lote             (N, con vencimiento)
           +-- ProductoProveedor (N, precio de referencia por proveedor)
           +-- MovimientoStock
           +-- DetalleVenta     (nullable: los ítems MANUAL no tienen producto)
           +-- DetalleCompra

Venta ---- DetalleVenta          Compra --- DetalleCompra
SesionCaja -- MovimientoCaja
SesionCaja -- Venta             (toda venta cobrada durante el turno)
SesionCaja -- Compra            (solo las pagadas de la caja)
```

`movimientos_caja.id_referencia` y `movimientos_stock.id_referencia` son polimórficas (apuntan a
una venta o a una compra según el origen), por eso no llevan FK.

### Decisiones de modelado

- **El stock tiene dos fuentes según el producto:** la tabla `stock` para los comunes y la suma
  de lotes activos para los que manejan lotes. Un producto con lotes **no lleva fila de `stock`**:
  esa fila no la lee nadie y no se actualiza cuando entra o sale mercadería por lote. Las ventas
  de estos productos consumen por **FEFO** —*first expired, first out*—, o sea el lote que vence
  antes, que no es lo mismo que el que entró antes.
- **El precio de referencia de un proveedor no es lo que se le pagó.** `producto_proveedor` es un
  catálogo de consulta que se carga a mano; lo que realmente se pagó sale del historial de
  compras. La vista que los cruza vive en `compras`, el único módulo que ya depende de productos
  y de proveedores; el catálogo lo tiene `productos`, porque `productos -> proveedores` ya existe
  y colgarlo del otro lado cerraría un ciclo.
- **`movimientos_stock` es la fuente de verdad de la trazabilidad.** Nunca se borra un
  movimiento: anular un comprobante genera movimientos de reversa que referencian al original.
  Es lo que permite reponer cada lote en la cantidad exacta que se le sacó.
- **El estado de un lote se calcula al leer**, no se almacena como verdad: depende del día en
  que se consulta.
- **Los importes copiados en los detalles son snapshots.** Cambiar el precio o el costo de un
  producto no reescribe el histórico ni la ganancia ya informada.
- **El corte de caja se congela al cerrarlo.** Es un documento contable: se guarda como quedó y
  no se recalcula.

### Concurrencia

Todo lo que lee una cantidad para después reescribirla toma el lock de la fila
(`SELECT ... FOR UPDATE`): sin eso, dos operaciones simultáneas sobre el mismo producto parten
del mismo valor leído, la segunda pisa a la primera y se vende de más sin que quede registrado.
Son tres puertas y una sola por recurso:

| Recurso | Consulta |
|---|---|
| Fila de `stock` de un producto | `StockRepository.findByIdProductoParaActualizar` |
| Lotes de un producto | `LoteRepository.findParaDescuentoFefo` |
| Sesión de caja abierta | `SesionCajaRepository.findAbiertaParaActualizar` |

`findParaDescuentoFefo` es la **única** puerta para bloquear los lotes de un producto, y su orden
—`fechaVencimiento ASC, id ASC`— es parte del contrato: todas las transacciones los toman en esa
secuencia, así que no puede haber ciclo entre ellas. Las operaciones que recorren lotes sueltos
—la reversa de una venta o de una compra, el ajuste manual por lote— hacen antes una pasada que
los bloquea a todos por esta consulta, en vez de tomarlos uno por uno en el orden en que
aparecen. Cuando además hay que bloquear stock y lotes del mismo producto, el orden es siempre
stock primero.

## Flujos principales

**Venta.** Se crea con sus líneas y descuenta stock en el momento (FEFO por lote si
corresponde). Queda `cobrada = false` hasta el cobro, que registra medio de pago y monto. Solo
el pago **en efectivo** asocia la venta al turno de caja y genera la entrada; tarjeta y
transferencia quedan registradas pero fuera del arqueo.

**Anulación de una venta.** Devuelve la mercadería al stock apoyándose en los movimientos que la
referencian —no en los detalles—, que es lo que permite reponer cada lote en la cantidad exacta
que se le sacó. Una venta **cobrada sí se anula**: un `EMPLEADO` puede anular las propias dentro
de una ventana de 7 días medida sobre la apertura de su turno, y un `ADMIN` cualquiera sin
ventana. Si movió efectivo, la plata vuelve como un movimiento `REVERSA` **en el turno abierto de
hoy** —que es de donde físicamente sale cuando el cliente vuelve con el ticket—, de modo que el
corte viejo no se toca y el de hoy cuadra con el efectivo real. Sin ningún turno abierto, la
anulación se rechaza.

**Compra.** Ingresa mercadería: crea el lote si el producto los maneja, o aumenta el stock
agregado. Con `pagoEnEfectivo` genera la salida de caja del turno abierto. Al anularla se
descuenta lo ingresado y se devuelve la plata, salvo que la mercadería ya se haya vendido o el
turno haya cerrado.

**Caja.** Un solo turno abierto a la vez, garantizado por índice único. El corte compara el
saldo esperado (saldo inicial + entradas − salidas) contra el conteo físico y guarda la
diferencia junto al desglose.

## Ventas creadas sin conexión

El front puede vender con internet caído: arma el ticket en el dispositivo, lo guarda en su cola
y lo manda en lote cuando la conexión vuelve, a `POST /api/ventas/v1/sync`. Eso obliga a cuatro
cosas que van contra las reglas del resto del sistema, y conviene saber cuáles son y por qué.

### Cuatro reglas que se rompen a propósito

**1. El id lo genera el cliente, y es UUIDv7.** El ticket ya existía en el dispositivo antes de
que el backend supiera de él, así que su uuid viene en el payload. Que sea **versión 7** no es
cosmético: la PK es el clustered index de InnoDB y un v4 es aleatorio de punta a punta, así que
cada inserción cae en una página al azar y la tabla que más crece del sistema es la que peor lo
pasa. Los 48 bits altos de un v7 son el timestamp, así que las inserciones vuelven a ser casi
secuenciales. El backend **valida la versión al recibir** —un v4 colado fragmenta igual— y genera
del mismo formato en las tablas que reciben ids del front: `ventas`, `detalles_ventas` y
`sesiones_caja`. Las otras trece siguen con v4, porque ninguna recibe ids del cliente.

Hibernate 7 trae `@UuidGenerator(style = VERSION_7)`, pero no sirve acá: un generador **genera
siempre** y pisaría el uuid que trae el ticket. Por eso el id es asignado y existe
`shared/Uuid7`. Como consecuencia, esas tres entidades implementan `Persistable`: sin eso Spring
Data decide que una entidad con id no es nueva y cada `save()` pasa por `merge()`, o sea un
`SELECT` que no devuelve nada antes de cada `INSERT`.

**2. El vendedor viaja en el payload.** Es la excepción a *la identidad sale siempre del JWT*.
Con una terminal y cambio de turno, el que está logueado cuando vuelve internet no es el que hizo
los tickets encolados. Se valida que el usuario **exista** y nada más: exigir que esté activo
dejaría los tickets de un empleado dado de baja rechazados para siempre, y la mercadería salió
igual. La mitigación es auditoría, no permiso: `ventas.id_usuario_sync` guarda quién sincronizó
—ese sí del JWT— y `origen = OFFLINE` marca por dónde entró.

**3. `created_at` y `deleted_at` los manda el cliente.** Es lo que más confunde a quien lee la
base a mano, así que:

| Columna | Qué significa | Quién la pone |
|---|---|---|
| `created_at` | Cuándo **ocurrió** el ticket | El front (online: el reloj del servidor) |
| `deleted_at` | Cuándo se **anuló** | El front (online: el reloj del servidor) |
| `sincronizado_en` | Cuándo **llegó a MySQL**. `NULL` en las ventas online | El backend, siempre |
| `updated_at` | Auditoría de la fila | El backend, siempre |

Un lote son veinte tickets que pasaron en momentos distintos y llegan todos juntos: si la fecha
la pusiera el `INSERT`, los veinte quedarían fechados en el mismo segundo y dos días después de
lo que ocurrió, y el resumen diario, los reportes y el arqueo saldrían todos corridos. La fecha
baja por **toda** la cadena —el movimiento de stock y el de caja del ticket también—, porque si
no el kardex muestra la mercadería saliendo después de la venta que la sacó. `@CreationTimestamp`
se fue de `Venta`, `DetalleVenta`, `MovimientoStock` y `MovimientoCaja`; en los dos movimientos
quedó un `@PrePersist` que fecha solo si nadie lo hizo, que es el caso de todo el flujo online.

Como la fecha pasó a ser un dato del cliente, se valida: no más de `sync.desfase-maximo-minutos`
en el futuro, no más de `sync.antiguedad-maxima-dias` en el pasado, y una anulación nunca anterior
a su propio ticket.

> **Hay una fecha que a propósito no sigue al front: la `REVERSA` de caja de una anulación.** La
> plata sale del turno abierto de hoy, así que el movimiento tiene que caer dentro de la vida de
> ese turno; fechado anteayer, el arqueo lo contaría —suma por sesión— pero el resumen de caja
> por fecha no.

**4. Un faltante de stock no rechaza el ticket.** Esto es una caja registradora, no un ecommerce:
la venta es física y ya ocurrió sobre el mostrador. Si el sistema decía 4 y se vendieron 10,
entonces el sistema estaba mal y las 10 unidades estaban en la góndola. La venta no produjo el
faltante, lo reveló. Así que entra un `AJUSTE` por la diferencia y **después** la `VENTA`
completa, en ese orden y en la misma transacción:

```
AJUSTE  +6   Regularización por venta offline <uuid>    4 → 10
VENTA  -10   el ticket, completo                       10 → 0
```

Registrar la venta parcial estaría mal por dos motivos: el kardex dejaría de sumar al stock, y la
anulación repondría 4 en vez de 10 —porque la reversa trabaja sobre los movimientos de tipo
`VENTA`—. El stock nunca pasa por un valor negativo. El ticket queda con `requiere_revision` y
aparece en `GET /api/ventas/v1/revision`; lo que corresponde hacer no es revisar el ticket, que
es correcto, sino **contar ese producto**.

### Cómo se procesa un lote

```
POST /api/ventas/v1/sync
  SyncVentasService        ordena por ocurridoEn/secuencia, acota el lote, arma los resultados
    ProcesadorEventoSync   UNA TRANSACCIÓN POR EVENTO
      CREAR                idempotente por uuid; descuenta con regularización; recalcula el total
      ANULAR               anula, o espera en anulaciones_pendientes si el ticket no llegó
      ABRIR_SESION         crea el turno con el uuid y la fecha del dispositivo
      CERRAR_SESION        calcula el saldo esperado y congela el corte
```

- **Una transacción por evento, no una por lote.** Un lote a medio procesar tiene que dejar la
  base consistente, porque es exactamente lo que pasa cuando la respuesta se pierde en el camino
  y el front reenvía. Por eso `ProcesadorEventoSync` es un bean aparte: una llamada interna no
  pasa por el proxy de Spring y no abriría ninguna transacción nueva. Cuando un evento no se
  puede aplicar, se lanza `EventoSyncException` justamente para que al salir del método
  transaccional se deshaga todo lo que ese evento hubiera escrito.
- **El orden es el del local, no el del array.** Es lo que hace que un ticket y su anulación en
  el mismo lote se apliquen en el orden en que pasaron, y que la apertura de un turno llegue
  antes que los tickets que cuelgan de ella.
- **Idempotencia por `tipo` + `uuid`.** Reenviar un lote entero no produce efectos nuevos, y esa
  es toda la recuperación que hace falta cuando una respuesta se pierde. El reenvío devuelve
  además **la misma respuesta**, marca de revisión incluida: si devolviera un `OK` pelado, el
  único caso en que la idempotencia importa sería el único en que el front nunca se entera.
- **Un `ANULAR` puede llegar antes que su `CREAR`.** Queda en `anulaciones_pendientes` —una tabla
  sin FK a `ventas`, justamente porque la venta puede no existir— y se aplica en la misma
  transacción en que el ticket se persiste.
- **El turno de caja también se sincroniza.** Sin eso, un corte largo que agarra un cambio de
  turno deja los tickets del turno nuevo sin dónde colgarse. Sigue valiendo que no puede haber dos
  turnos abiertos a la vez (`uk_sesiones_una_abierta`), y que ningún movimiento se imputa a un
  turno cerrado: un ticket que llega después del corte de su propio turno responde `ERROR` en vez
  de colarse en un documento ya firmado.

### Los importes de ventas son `DECIMAL`

`ventas.total`, `ventas.monto_recibido`, `detalles_ventas.precio_unitario` y `costo_unitario`
están en `DECIMAL(12,2)` y se manejan como `BigDecimal`. El motivo es el sync: el total lo suma
también el front, y las dos cuentas tienen que dar exactamente igual. Con `float` cada diferencia
de redondeo aparecería como un ticket "para revisar" que no tiene nada.

**El resto del sistema sigue en `float`** —compras, caja, precios de productos—, que es la deuda
técnica #1 del roadmap. Las fronteras donde un importe exacto se degrada al cruzar pasan todas
por `shared/Importes`, que además evita la trampa de `BigDecimal.valueOf(float)`: ensancha a
`double` y saca a la luz la basura binaria que el `float` escondía (`1200.05f` termina en
`1200.0499877929688`). El pasaje correcto es por `Float.toString`.

## API REST

Todas las rutas son `/{recurso}/v1/...` y requieren `Authorization: Bearer <token>`, salvo
`/api/auth/v1/**` y la documentación.

| Módulo | Ruta base |
|---|---|
| Auth | `/api/auth/v1` |
| Usuarios | `/api/users/v1` |
| Categorías | `/api/categorias/v1` |
| Proveedores | `/api/proveedores/v1` |
| Productos | `/api/productos/v1` |
| Inventario | `/api/inventario/v1` |
| Ventas | `/api/ventas/v1` |
| Compras | `/api/compras/v1` |
| Caja | `/api/caja/v1` |
| Reportes | `/api/reportes/v1` |

El detalle de cada endpoint —request, response y errores— está en
[`docs/api-endpoints.md`](docs/api-endpoints.md). Swagger UI en `/swagger-ui/index.html`.

El resto de la documentación está indexado en [`docs/README.md`](docs/README.md).

### Manejo de errores

`GlobalExceptionHandler` traduce las excepciones a un cuerpo uniforme
(`{ "error": ..., "timestamp": ... }`):

| Status | Causa |
|---|---|
| `400` | Validación, regla de negocio, JSON ilegible o parámetro mal formado |
| `401` | Token ausente, inválido, expirado o de usuario dado de baja |
| `403` | Autenticado sin permisos |
| `404` | Recurso o ruta inexistente |
| `409` | Choque con una restricción de datos |
| `500` | Error interno (queda en el log con stacktrace) |

## Base de datos y despliegue

Scripts en `script/database/`:

| Script | Contenido |
|---|---|
| `00_init.sql` | Esquema existente, se conserva por compatibilidad |
| `00_init_limpio.sql` | Esquema final autocontenido para una base nueva |
| `01_seed.sql` | Datos de desarrollo (admin, catálogo de ejemplo) |
| `02_parche_migraciones.sql` | Parche acumulado para una base existente anterior al esquema final |
| `02_seed_productos.sql`, `03_seed_stock.sql` | Datos de prueba opcionales: catálogo con existencias |
| `04` a `13` | Migraciones numeradas, una por cambio de esquema. Ver el `CHANGELOG` de cada versión |

El esquema está alineado con las entidades: la app puede arrancar con
`spring.jpa.hibernate.ddl-auto=validate` y no reporta discrepancias. Las migraciones numeradas se
aplican **a mano, en orden y con la aplicación detenida**; cada una abre con una consulta
informativa de los datos que podrían frenar el `ALTER` y cierra con una de verificación. Una
instalación nueva no las necesita: `00_init_limpio.sql` ya las trae incorporadas. Incorporar
Flyway es trabajo pendiente.

Configuración por variables de entorno (ver `.env.example`): `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET` (obligatoria), `JWT_EXPIRATION_HOURS`, `SERVER_PORT`,
`CORS_ALLOWED_ORIGINS`, `JPA_DDL_AUTO`. La sincronización agrega `SYNC_LOTE_MAXIMO`,
`SYNC_DESFASE_MAXIMO_MINUTOS` y `SYNC_ANTIGUEDAD_MAXIMA_DIAS`, y la anulación
`ventas.anulacion.dias`; todas tienen valor por defecto.

## Patrones Utilizados

- **Módulos con API explícita:** cada módulo expone una interfaz `XxxApi` y oculta el resto.
- **DTO Pattern:** entidades y contratos de API separados; el mapeo es explícito en el servicio.
- **Repository Pattern:** Spring Data JPA.
- **Soft Delete:** `deleted_at`, con las consultas filtrando por `deletedAt IS NULL`.
- **Snapshot de datos históricos:** nombre, precio y costo copiados en los detalles.
- **Movimientos append-only:** el stock y la caja se corrigen con reversas, nunca borrando.
- **Auditoría automática:** `@UpdateTimestamp` en todas las entidades. `@CreationTimestamp` en
  todas menos cuatro —`Venta`, `DetalleVenta`, `MovimientoStock` y `MovimientoCaja`—, donde la
  fecha puede venir del dispositivo y la anotación la pisaría en cada `INSERT`.
- **Id asignado con `Persistable`:** en las tres tablas que reciben ids del front, para que
  `save()` no haga un `SELECT` antes de cada `INSERT`.
- **Inyección por constructor:** vía Lombok.

## Estado y deuda conocida

- **Los tests son unitarios y con mocks**: 358 en 45 archivos (JUnit 6 + Mockito), un archivo
  por área de un servicio. No hay tests de integración contra una base real —
  `MinimarketApplicationTests.contextLoads` es el único que necesita MySQL levantado y va
  marcado con `@Tag("integracion")`—, así que las consultas JPQL y los índices se verifican a
  mano. Es la deuda que más se siente al tocar el flujo offline: varios de los problemas que
  aparecieron en esta versión —el `SELECT` antes de cada `INSERT`, la fecha del retiro del
  corte— solo se vieron corriendo contra MySQL de verdad.
- Las migraciones se aplican a mano (falta Flyway).
- **Los importes usan `float`, salvo los de ventas.** `ventas` y `detalles_ventas` migraron a
  `DECIMAL` + `BigDecimal` porque el sync obliga a que el total del backend y el del front
  coincidan exactamente. Compras, caja y los precios del catálogo siguen en `float` y acumulan
  error de redondeo; las fronteras entre los dos mundos pasan por `shared/Importes`.
- Queda un directorio `controller/` vacío en la raíz del paquete, resto de la estructura previa.
