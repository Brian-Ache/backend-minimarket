# Arquitectura — backend-minimarket v0.5.0

Backend de un punto de venta para minimarket: catálogo, ventas con cobro, compras a proveedores,
inventario con lotes, caja con arqueo y reportes.

## Stack Tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| Java | 21 | Lenguaje |
| Spring Boot | 3.5.13 | Framework principal |
| Spring Data JPA / Hibernate | 6.6 | ORM y persistencia |
| Spring Security | -- | Autenticación JWT y autorización por rol |
| JJWT | 0.12.6 | Firma y validación de tokens |
| SpringDoc OpenAPI | 2.5.0 | Documentación de API |
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
  acepta el id de usuario del cliente.
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

16 tablas. Convenciones transversales: PK `UUID` (`BINARY(16)`), borrado lógico con `deleted_at`
(NULL = activo) y auditoría `created_at` / `updated_at`.

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
transferencia quedan registradas pero fuera del arqueo. Anular una venta no cobrada devuelve la
mercadería al stock; una venta cobrada no se anula.

**Compra.** Ingresa mercadería: crea el lote si el producto los maneja, o aumenta el stock
agregado. Con `pagoEnEfectivo` genera la salida de caja del turno abierto. Al anularla se
descuenta lo ingresado y se devuelve la plata, salvo que la mercadería ya se haya vendido o el
turno haya cerrado.

**Caja.** Un solo turno abierto a la vez, garantizado por índice único. El corte compara el
saldo esperado (saldo inicial + entradas − salidas) contra el conteo físico y guarda la
diferencia junto al desglose.

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
| `04` a `11` | Migraciones numeradas, una por cambio de esquema. Ver el `CHANGELOG` de cada versión |

El esquema está alineado con las entidades: la app puede arrancar con
`spring.jpa.hibernate.ddl-auto=validate` y no reporta discrepancias. Las migraciones numeradas se
aplican **a mano, en orden y con la aplicación detenida**; cada una abre con una consulta
informativa de los datos que podrían frenar el `ALTER` y cierra con una de verificación. Una
instalación nueva no las necesita: `00_init_limpio.sql` ya las trae incorporadas. Incorporar
Flyway es trabajo pendiente.

Configuración por variables de entorno (ver `.env.example`): `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET` (obligatoria), `JWT_EXPIRATION_HOURS`, `SERVER_PORT`,
`CORS_ALLOWED_ORIGINS`, `JPA_DDL_AUTO`.

## Patrones Utilizados

- **Módulos con API explícita:** cada módulo expone una interfaz `XxxApi` y oculta el resto.
- **DTO Pattern:** entidades y contratos de API separados; el mapeo es explícito en el servicio.
- **Repository Pattern:** Spring Data JPA.
- **Soft Delete:** `deleted_at`, con las consultas filtrando por `deletedAt IS NULL`.
- **Snapshot de datos históricos:** nombre, precio y costo copiados en los detalles.
- **Movimientos append-only:** el stock y la caja se corrigen con reversas, nunca borrando.
- **Auditoría automática:** `@CreationTimestamp` / `@UpdateTimestamp`.
- **Inyección por constructor:** vía Lombok.

## Estado y deuda conocida

- **Los tests son unitarios y con mocks**: 258 en 37 archivos (JUnit 5 + Mockito), un archivo
  por área de un servicio. No hay tests de integración contra una base real —
  `MinimarketApplicationTests.contextLoads` es el único que necesita MySQL levantado—, así que
  las consultas JPQL y los índices se verifican a mano.
- Las migraciones se aplican a mano (falta Flyway).
- **Los importes usan `float`.** Para dinero corresponde `DECIMAL` + `BigDecimal`; mientras
  siga así, los totales acumulan error de redondeo.
- Queda un directorio `controller/` vacío en la raíz del paquete, resto de la estructura previa.
