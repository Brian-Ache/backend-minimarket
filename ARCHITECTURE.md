# Arquitectura — backend-minimarket v0.2.0

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
+-- shared/                        # SecurityUtils + excepciones y handler global
+-- modules/
    +-- auth/                      # Login, refresh, logout, verificación y reseteo
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
usuarios  -> auth                      (para revocar sesiones al dar de baja)
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
         verifica que el usuario siga activo y habilitado,
         y publica el rol como authority ROLE_<ROL>
Refresh-> rota el par (revoca el anterior)
Logout -> revoca el refresh token (idempotente)
```

- **Identidad:** siempre desde el JWT, vía `SecurityUtils.getCurrentUserId()`. Ningún endpoint
  acepta el id de usuario del cliente.
- **Roles:** `ADMIN` y `EMPLEADO`, aplicados con `@EnableMethodSecurity`. Las reglas por URL
  están en `SecurityConfig`; `@PreAuthorize` se usa solo donde el permiso depende de quién es el
  dueño del recurso (`/api/users/{id}`).
- **Contraseñas:** BCrypt.
- **Baja de usuario:** revoca sus sesiones; su JWT deja de servir en la request siguiente.
- **Configuración obligatoria:** `JWT_SECRET`. Sin él la app no arranca.
- **CSRF** deshabilitado (API stateless); **CORS** configurable por `CORS_ALLOWED_ORIGINS`.

> El access token no se puede revocar antes de que expire: el logout invalida el refresh token,
> pero el JWT ya emitido sigue siendo válido hasta su vencimiento. Con 24 h por defecto la
> ventana es amplia; conviene bajarla a 1–2 h y apoyarse en el refresh. Sí se corta de inmediato
> el acceso de un usuario dado de baja, porque el filtro lo verifica en cada request.

| Operación | ADMIN | EMPLEADO |
|---|:---:|:---:|
| Vender, cobrar, comprar, mover caja e inventario | ✅ | ✅ |
| Consultar catálogo · ver y editar su propio usuario | ✅ | ✅ |
| Alta/baja de usuarios · escritura de catálogo | ✅ | ❌ |
| Anular ventas y compras · corte de caja · reportes | ✅ | ❌ |

## Modelo de Datos

15 tablas. Convenciones transversales: PK `UUID` (`BINARY(16)`), borrado lógico con `deleted_at`
(NULL = activo) y auditoría `created_at` / `updated_at`.

| Tabla | Propósito |
|---|---|
| `usuarios` | Usuarios del sistema (ADMIN / EMPLEADO) |
| `auth_tokens` | Tokens de un solo uso: verificación y reseteo de contraseña |
| `refresh_tokens` | Sesiones activas (hash del refresh token) |
| `categorias` | Categorías de producto |
| `proveedores` | Proveedores |
| `productos` | Catálogo. El stock **no** vive acá |
| `stock` | Existencias agregadas por producto (una fila activa por producto) |
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

Producto --+-- Stock            (1 fila activa)
           +-- Lote             (N, con vencimiento)
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
  de lotes activos para los que manejan lotes. Las ventas de estos últimos consumen por FIFO
  según fecha de vencimiento.
- **`movimientos_stock` es la fuente de verdad de la trazabilidad.** Nunca se borra un
  movimiento: anular un comprobante genera movimientos de reversa que referencian al original.
  Es lo que permite reponer cada lote en la cantidad exacta que se le sacó.
- **El estado de un lote se calcula al leer**, no se almacena como verdad: depende del día en
  que se consulta.
- **Los importes copiados en los detalles son snapshots.** Cambiar el precio o el costo de un
  producto no reescribe el histórico ni la ganancia ya informada.
- **El corte de caja se congela al cerrarlo.** Es un documento contable: se guarda como quedó y
  no se recalcula.

## Flujos principales

**Venta.** Se crea con sus líneas y descuenta stock en el momento (FIFO por lote si
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

Scripts en `script/database/`, pensados para aplicarse en orden:

| Script | Contenido |
|---|---|
| `00_init.sql` | Esquema completo. Suficiente para una base nueva |
| `01_seed.sql` | Datos de desarrollo (admin, catálogo de ejemplo) |
| `02_migracion_fase3.sql` | Reversas de stock, unicidad de stock y de sesión abierta |
| `03_migracion_fase4.sql` | Desglose del corte, costo por línea de venta |
| `04_migracion_fase5.sql` | `refresh_token` → `refresh_tokens` |

El esquema está alineado con las entidades: la app puede arrancar con
`spring.jpa.hibernate.ddl-auto=validate` y no reporta discrepancias. Las migraciones se aplican
a mano; incorporar Flyway es trabajo pendiente.

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

- **No hay tests automatizados** más allá de la carga de contexto. Es la deuda más importante:
  toda la verificación de la v0.2.0 fue manual.
- Los listados de ventas y compras **no están paginados**.
- Las migraciones se aplican a mano (falta Flyway).
- **Los importes usan `float`.** Para dinero corresponde `DECIMAL` + `BigDecimal`; mientras
  siga así, los totales acumulan error de redondeo.
- El envío de emails no está implementado, así que la verificación de cuenta y el reseteo
  autogestionado de contraseña quedan fuera del circuito.
- **CORS no permite `PATCH`** (`CorsConfig` habilita GET, POST, PUT, DELETE y OPTIONS), pero
  `PATCH /api/users/v1/{id}` existe: desde el navegador el preflight lo rechaza. Es agregar el
  método a la lista.
- Queda un directorio `controller/` vacío en la raíz del paquete, resto de la estructura previa.
