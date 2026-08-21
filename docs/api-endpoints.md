# API Endpoints — MiniMarket POS

Base URL: `http://localhost:8080`

---

## Formato de errores común

```json
{
  "error": "mensaje de error",
  "timestamp": "2026-07-12T15:00:00"
}
```

| Status | Causa |
| ------ | ---------------------------------------------------------------- |
| `400` | Validación, regla de negocio, JSON ilegible o parámetro mal formado |
| `401` | Token ausente, inválido, expirado o de un usuario dado de baja |
| `403` | Autenticado pero sin permisos para ese recurso |
| `404` | Recurso inexistente o eliminado · ruta inexistente |
| `409` | Choque con una restricción de datos existente |
| `500` | Error interno (queda registrado en el log del servidor con stacktrace) |

Errores de validación (`400`):

```json
{
  "error": "Validation failed",
  "details": ["El campo no puede estar vacío"],
  "timestamp": "2026-07-12T15:00:00"
}
```

---

## Convenciones

- **Auth:** todas las rutas excepto `/api/auth/v1/**`, `/swagger-ui/**`, `/v3/api-docs/**` requieren header `Authorization: Bearer <token>`
- **IDs:** todos UUID v4
- **Fechas:** ISO 8601 (`2026-07-12T15:00:00`)
- **Soft delete:** GET por ID de registro eliminado responde `404`
- **Identidad:** el usuario que ejecuta la operación se toma **del JWT**. El header `idUsuario`
  fue eliminado de todos los endpoints; si se envía, se ignora
- **Roles:** `SUPERADMIN` > `ADMIN` > `EMPLEADO`, en jerarquía: cada rol puede todo lo del rol
  de abajo, y además gestiona (alta, bloqueo y baja) a los usuarios de nivel inferior. La
  jerarquía es estricta, así que **un ADMIN no puede tocar a otro ADMIN**. Ver la matriz de
  permisos más abajo
- **Estado de cuenta:** `PENDIENTE` (creada, sin acceso todavía) · `ACTIVO` (opera) ·
  `BLOQUEADO` (acceso suspendido, reversible). Solo un usuario `ACTIVO` puede loguearse y
  operar; el bloqueo tiene efecto inmediato sobre las sesiones abiertas
- **Códigos de auth:** `401` token ausente, inválido, expirado o de un usuario dado de baja
  (el front debe reautenticar) · `403` autenticado pero sin permisos para ese recurso
- **El rol se lee de la base en cada request**, no del claim del token: bloquear a alguien,
  darlo de baja o cambiarle el rol tiene efecto en la llamada siguiente, sin esperar a que su
  JWT expire. El claim `rol` del token es informativo, para que el front sepa qué mostrar
- **Swagger UI:** `/swagger-ui/index.html`
- **OpenAPI spec:** `/v3/api-docs`

### Matriz de permisos

| Operación | SUPERADMIN | ADMIN | EMPLEADO |
|---|:---:|:---:|:---:|
| Vender, cobrar, comprar | ✅ | ✅ | ✅ |
| Abrir caja, movimientos manuales | ✅ | ✅ | ✅ |
| Inventario: stock, lotes, ajustes | ✅ | ✅ | ✅ |
| Consultar catálogo (GET productos/categorías/proveedores) | ✅ | ✅ | ✅ |
| Ver y editar su propio usuario, cambiar su contraseña | ✅ | ✅ | ✅ |
| Crear/editar/borrar productos, categorías y proveedores | ✅ | ✅ | ❌ |
| Anular ventas y compras (DELETE) | ✅ | ✅ | ❌ |
| Corte de caja | ✅ | ✅ | ❌ |
| Reportes | ✅ | ✅ | ❌ |
| Listar y ver usuarios | ✅ | ✅ | ❌ |
| Invitar, dar de alta, bloquear y dar de baja a un EMPLEADO | ✅ | ✅ | ❌ |
| Invitar, dar de alta, bloquear y dar de baja a un ADMIN | ✅ | ❌ | ❌ |
| Promover o degradar entre ADMIN y EMPLEADO | ✅ | ❌ | ❌ |
| Alta, bloqueo, baja o asignación del rol SUPERADMIN | ❌ | ❌ | ❌ |

Nadie gestiona a un usuario de su mismo nivel ni de uno superior, y **nadie se bloquea ni se
borra a sí mismo**. De ahí que no exista alta de SUPERADMIN por API: la llave maestra sale del
seed de la base (`01_seed.sql`). Si el alta de superadmins fuera
un endpoint, tomar una sesión de superadmin alcanzaría para fabricarse otro.

Cambiar la contraseña exige conocer la actual, así que **ni el ADMIN ni el SUPERADMIN pueden
hacerlo por otro usuario**: para eso está el flujo de reseteo.

---

## 1. Auth — `/api/auth/v1`

Todas públicas (no requieren token).

> **`POST /api/auth/v1/register` fue dado de baja.** El alta la hace un administrador,
> invitando ([`POST /api/users/v1/invitaciones`](#post-apiusersv1invitaciones)) o directamente
> ([`POST /api/users/v1`](#post-apiusersv1)). La lógica de autorregistro sigue implementada en
> `AuthApi.register` pero sin endpoint: no hay caso de uso para que alguien se dé de alta solo
> en el sistema de un comercio.

### `POST /api/auth/v1/login`

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Response `200`:**
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string",
  "usuario": { "...UsuarioResponse" }
}
```

---

### `POST /api/auth/v1/refresh`

Rota el refresh token (invalida el anterior, genera uno nuevo).

**Request:**
```json
{
  "refreshToken": "string"
}
```

**Response `200`:** nuevo par accessToken + refreshToken

---

### `POST /api/auth/v1/logout`

Revoca el refresh token. Es idempotente: desloguear un token ya revocado o inexistente
también devuelve `204`.

**Request:**
```json
{ "refreshToken": "string" }
```

**Response `204`**

> El refresh token va en el **body**, no en el header. El access token sigue siendo válido
> hasta que expire (`jwt.expiration-hours`).

---

### `POST /api/auth/v1/verify-email`

**Request:**
```json
{ "token": "string" }
```

**Response `200`**

---

### `POST /api/auth/v1/invitacion/aceptar`

Cierre del alta por invitación: define la contraseña y pasa la cuenta a `ACTIVO`. **Público** —
quien la acepta todavía no tiene contraseña, su credencial es el token del mail.

**Request:**
```json
{
  "token": "string (el del enlace del mail)",
  "password": "string (min 8, max 72)"
}
```

**Response `200`** — después hay que loguearse normalmente.

**Errores `400`:** token inválido, vencido o ya usado · token de otro tipo · la cuenta fue
bloqueada o dada de baja entre la invitación y la aceptación

---

### `POST /api/auth/v1/password-reset`

Solicita reseteo de contraseña y **manda el mail** con el enlace. Acepta email o username; el
mail sale siempre al email de la cuenta.

**Request:**
```json
{ "username": "string (email o nombre de usuario)" }
```

**Response `200`** — siempre, exista o no la cuenta, y **también si el envío falla**. Un `502`
solo para las cuentas que existen revelaría cuáles existen; el fallo queda en el log del
servidor.

El enlace vence en **1 hora** y sirve una sola vez.

---

### `POST /api/auth/v1/password-reset/confirm`

Confirma el reseteo con el token generado.

**Request:**
```json
{
  "token": "string",
  "newPassword": "string (min 8, max 72)"
}
```

**Response `200`**

---

## 2. Usuarios — `/api/users/v1`

### `POST /api/users/v1/invitaciones`

**Alta por invitación — el flujo recomendado.** Crea la cuenta en estado `PENDIENTE` y le manda
un mail a la persona con un enlace para que **defina su propia contraseña**. Quien invita nunca
conoce la contraseña del invitado.

**ADMIN o SUPERADMIN**, con las mismas reglas de jerarquía que el alta directa: solo se invita
por debajo del propio nivel.

**Request:**
```json
{
  "nombre": "string (max 50)",
  "apellido": "string (max 50)",
  "email": "email (max 100)",
  "username": "string (max 50, opcional)",
  "rol": "ADMIN | EMPLEADO (opcional, default EMPLEADO)"
}
```

Si no se manda `username`, se deriva de la parte local del email (`ana.perez@…` → `ana.perez`),
agregando un sufijo numérico si ya estaba tomado. Un `username` **explícito** ya en uso, en
cambio, da `400`: ahí sí hubo una elección que respetar.

**Response `201`:** `{ ...UsuarioResponse }` con `estado: "PENDIENTE"`

**Errores:** `400` email ya registrado o username explícito en uso · `403` sin rol ADMIN o rol
pedido no permitido · `502` el mail no se pudo enviar

> Si el envío falla, **el alta se revierte**: no queda una cuenta muerta ocupando ese email y
> ese username que nadie puede activar. El `502` distingue "reintentá" de "corregí los datos".

El enlace vence a las **72 horas**. Vencido, se usa el reenvío.

---

### `POST /api/users/v1/{id}/invitaciones/reenviar`

Manda la invitación de nuevo, con un token nuevo — **el anterior queda invalidado**, para que
cada reenvío no deje otra puerta abierta hasta que expire.

Solo sobre cuentas en estado `PENDIENTE`, y con las mismas reglas de jerarquía que `bloquear`.

**Response `204`**

**Errores:** `400` la cuenta no está pendiente · `403` el objetivo no está por debajo tuyo ·
`502` el mail no se pudo enviar

---

### `POST /api/users/v1`

Alta **directa**, con una contraseña elegida por quien la crea. Sigue disponible para altas sin
mail de por medio (importar usuarios, entornos sin SMTP); para el día a día está la
[invitación](#post-apiusersv1invitaciones), donde la contraseña la elige su dueño.

**ADMIN o SUPERADMIN** (`403` para EMPLEADO). El usuario se crea en estado `ACTIVO`, listo para
loguearse.

Solo se puede dar de alta **por debajo del propio nivel**: el SUPERADMIN crea ADMIN y EMPLEADO,
el ADMIN solo EMPLEADO. `rol` es opcional y por defecto es `EMPLEADO`.

**Request:**
```json
{
  "nombre": "string (max 50)",
  "apellido": "string (max 50)",
  "email": "email (max 100)",
  "username": "string (max 50)",
  "password": "string (min 8, max 72)",
  "rol": "ADMIN | EMPLEADO (opcional, default EMPLEADO)"
}
```

**Response `201`:** `{ ...UsuarioResponse }`

**Errores:** `400` email o username ya en uso · `403` sin rol ADMIN, o el rol pedido no está por
debajo del propio (`"Un ADMIN no puede dar de alta a un ADMIN"`)

---

### `GET /api/users/v1/me`

Perfil del usuario autenticado.

**Response `200`:** `{ ...UsuarioResponse }`

---

### `GET /api/users/v1/{id}`

**Response `200`:** `{ ...UsuarioResponse }`

**Error `404`:** si no existe o fue eliminado

---

### `GET /api/users/v1`

Lista todos los usuarios activos.

**Response `200`:** `[ ...UsuarioResponse ]`

---

### `PATCH /api/users/v1/{id}`

Actualiza nombre y/o apellido.

**Request:**
```json
{
  "nombre": "string (max 50, opcional)",
  "apellido": "string (max 50, opcional)"
}
```

**Response `200`:** `{ ...UsuarioResponse }`

---

### `PATCH /api/users/v1/{id}/rol`

Promueve o degrada a un usuario. Va aparte del `PATCH` general porque ese lo puede llamar el
dueño del recurso sobre sí mismo, y **nadie se cambia el rol solo**.

Se exige jerarquía **por partida doble**: el objetivo tiene que estar por debajo tuyo *y* el rol
nuevo también. Así un ADMIN no puede promover a un EMPLEADO para fabricarse un par, y `SUPERADMIN`
nunca es un valor asignable.

En la práctica: **solo el SUPERADMIN mueve gente entre ADMIN y EMPLEADO.**

**Request:**
```json
{ "rol": "ADMIN | EMPLEADO" }
```

**Response `200`:** `{ ...UsuarioResponse }` con el rol nuevo

**Errores:** `400` ya tiene ese rol · `400` es tu propia cuenta · `403` el objetivo no está por
debajo tuyo (`"Un ADMIN no puede cambiarle el rol a un ADMIN"`) o el rol pedido no lo está
(`"Un ADMIN no puede asignar el rol ADMIN"`)

> El cambio corta las sesiones del usuario: tiene que volver a loguearse para recibir un token
> que declare el rol nuevo. Sus **permisos** reales, en cambio, cambian ya en la request
> siguiente — el backend lee el rol de la base en cada llamada, no del token.

---

### `POST /api/users/v1/{id}/bloquear`

Suspende el acceso **sin borrar la cuenta**: el usuario conserva su historial de ventas,
compras y movimientos, y puede reactivarse. Corta sus sesiones abiertas, así que el bloqueo es
inmediato y no espera a que expire su token.

**ADMIN o SUPERADMIN**, y solo sobre usuarios de nivel inferior: un ADMIN bloquea EMPLEADO, el
SUPERADMIN también bloquea ADMIN. Al SUPERADMIN no lo bloquea nadie.

**Response `200`:** `{ ...UsuarioResponse }` con `estado: "BLOQUEADO"`

**Errores:** `400` ya está bloqueado · `400` es tu propia cuenta · `403` el objetivo no está por
debajo tuyo (`"Un ADMIN no puede bloquear a un ADMIN"`)

---

### `POST /api/users/v1/{id}/desbloquear`

Devuelve el acceso. El usuario tiene que volver a iniciar sesión.

**Mismas reglas de jerarquía que `bloquear`.**

**Response `200`:** `{ ...UsuarioResponse }` con `estado: "ACTIVO"`

**Errores:** `400` el usuario no está bloqueado · `403` el objetivo no está por debajo tuyo

---

### `DELETE /api/users/v1/{id}`

Baja lógica de la cuenta y revocación de sus sesiones. Para suspender temporalmente a alguien
usar `bloquear`, que es reversible.

**Mismas reglas de jerarquía que `bloquear`**, incluida la propia cuenta: nadie se da de baja a
sí mismo.

**Response `204`**

**Errores:** `400` es tu propia cuenta · `403` el objetivo no está por debajo tuyo

---

### `POST /api/users/v1/{id}/change-password`

**Request:**
```json
{
  "passActual": "string",
  "nuevoPass": "string (min 8, max 72)"
}
```

**Response `200`**

**Error `400`:** si la contraseña actual no coincide

---

### UsuarioResponse

```json
{
  "id": "UUID",
  "nombre": "string",
  "apellido": "string",
  "username": "string",
  "email": "string",
  "rol": "SUPERADMIN | ADMIN | EMPLEADO",
  "estado": "PENDIENTE | ACTIVO | BLOQUEADO",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

---

## 3. Productos — `/api/productos/v1`

### `POST /api/productos/v1`

**Request:**
```json
{
  "nombre": "string (max 200)",
  "barcode": "string (max 100, único)",
  "precio": "float (>= 0)",
  "manejaLotes": "boolean",
  "costo": "float (>= 0, opcional)",
  "margen": "float (>= 0, opcional)",
  "idCategoria": "UUID (opcional)",
  "idProveedor": "UUID (opcional)"
}
```

**Response `200`:** `{ ...ProductoResponse }`

**Error `400`:** si el barcode ya existe, o la categoría/proveedor no existen

---

### `GET /api/productos/v1`

Lista todos los productos activos. Acepta filtros opcionales.

**Query params:** `?categoria=UUID&proveedor=UUID`

**Response `200`:** `[ ...ProductoResponse ]`

---

### `GET /api/productos/v1/{id}`

**Response `200`:** `{ ...ProductoResponse }`

---

### `GET /api/productos/v1/barcode/{barcode}`

Busca por código de barras.

**Response `200`:** `{ ...ProductoResponse }`

---

### `GET /api/productos/v1/search`

Búsqueda por nombre (case-insensitive, top 20).

**Query params:** `?q=texto`

**Response `200`:** `[ ...ProductoResponse ]`

---

### `PUT /api/productos/v1/{id}`

**Request:** mismo body que POST

**Response `200`:** `{ ...ProductoResponse }`

---

### `DELETE /api/productos/v1/{id}`

Soft delete.

**Response `204`**

---

### ProductoResponse

```json
{
  "id": "UUID",
  "nombre": "string",
  "barcode": "string",
  "precio": "float",
  "manejaLotes": "boolean",
  "costo": "float | null",
  "margen": "float | null",
  "categoria": { "...CategoriaResponse" } | null,
  "proveedor": { "...ProveedorResponse" } | null
}
```

---

## 4. Categorías — `/api/categorias/v1`

### `POST /api/categorias/v1`

**Request:**
```json
{
  "nombre": "string (max 100, único)",
  "descripcion": "string (max 255, opcional)"
}
```

**Response `200`:** `{ ...CategoriaResponse }`

---

### `GET /api/categorias/v1`

**Response `200`:** `[ ...CategoriaResponse ]`

---

### `GET /api/categorias/v1/{id}`

**Response `200`:** `{ ...CategoriaResponse }`

---

### `PUT /api/categorias/v1/{id}`

**Request:** mismo body que POST

**Response `200`:** `{ ...CategoriaResponse }`

---

### `DELETE /api/categorias/v1/{id}`

**Response `204`**

---

### CategoriaResponse

```json
{
  "id": "UUID",
  "nombre": "string",
  "descripcion": "string | null"
}
```

---

## 5. Proveedores — `/api/proveedores/v1`

### `POST /api/proveedores/v1`

**Request:**
```json
{
  "nombre": "string (max 150)",
  "telefono": "string (max 50, opcional)",
  "email": "string (max 100, opcional)",
  "direccion": "string (max 255, opcional)"
}
```

**Response `200`:** `{ ...ProveedorResponse }`

---

### `GET /api/proveedores/v1`

**Response `200`:** `[ ...ProveedorResponse ]`

---

### `GET /api/proveedores/v1/{id}`

**Response `200`:** `{ ...ProveedorResponse }`

---

### `PUT /api/proveedores/v1/{id}`

**Request:** mismo body que POST

**Response `200`:** `{ ...ProveedorResponse }`

---

### `DELETE /api/proveedores/v1/{id}`

**Response `204`**

---

### ProveedorResponse

```json
{
  "id": "UUID",
  "nombre": "string",
  "telefono": "string | null",
  "email": "string | null",
  "direccion": "string | null"
}
```

---

## 6. Ventas — `/api/ventas/v1`

### `POST /api/ventas/v1`

Registra una venta con sus detalles. Si el producto maneja lotes, descuenta del lote más próximo a vencer (FIFO). Si no, descuenta del stock global.

**Request:**
```json
{
  "detalles": [
    {
      "tipo": "PRODUCTO | MANUAL",
      "cantidad": "int",
      "idProducto": "UUID | null (si MANUAL)",
      "nombreManual": "string | null (si PRODUCTO)",
      "precioUnitario": "float (requerido si MANUAL, ignorado si PRODUCTO)"
    }
  ]
}
```

> `idSesion` ya no se envía: la sesión de caja se resuelve al cobrar, y solo si el pago es
> en efectivo.

**Response `200`:**
```json
{
  "id": "UUID",
  "fecha": "datetime",
  "total": "float",
  "detalles": [ "...DetalleVentaResponse" ],
  "cobrada": false,
  "fechaCobro": null,
  "metodoPago": null,
  "montoRecibido": null
}
```

**Error `400`:** stock insuficiente · sin detalles · cantidad <= 0

---

### `POST /api/ventas/v1/{id}/cobrar`

Marca una venta como cobrada.

**Solo el pago en efectivo impacta en la caja.** Si `metodoPago` es `EFECTIVO`, la venta se
asocia a la sesión abierta y genera la entrada automática; con `TARJETA` o `TRANSFERENCIA` la
venta queda igualmente cobrada y registrada con su medio de pago, pero no toca el arqueo —que
cuenta billetes— ni requiere que haya una caja abierta.

**Request:**
```json
{
  "montoRecibido": "float (>= total de la venta)",
  "metodoPago": "EFECTIVO | TARJETA | TRANSFERENCIA"
}
```

**Response `200`:**
```json
{
  "venta": { "...VentaResponse" },
  "cambio": "float (solo en EFECTIVO; 0 en los demás medios)"
}
```

**Errores `400`:** ya está cobrada · monto recibido < total · método de pago inválido ·
`EFECTIVO` sin ninguna sesión de caja abierta

---

### `GET /api/ventas/v1/resumen/diario`

Resumen de las ventas **cobradas** del día, desglosado por medio de pago. Filtra por
**fecha de cobro**, no de creación: una venta abierta ayer y cobrada hoy es plata de hoy.

**Query params:** `?fecha=2026-07-12` (opcional, default hoy)

**Response `200`:** `{ ...ResumenVentas }`

---

### `GET /api/ventas/v1/resumen/sesion/{idSesion}`

Mismo desglose, acotado a un turno de caja. Complementa el corte, que solo cuenta efectivo:
acá se ve cuánto entró por tarjeta y transferencia en ese turno.

**Response `200`:** `{ ...ResumenVentas }`

---

### ResumenVentas

```json
{
  "fecha": "date",
  "cantidadVentas": "int",
  "totalVentas": "float",
  "totalEfectivo": "float",
  "totalTarjeta": "float",
  "totalTransferencia": "float"
}
```

---

### `GET /api/ventas/v1/{id}`

**Response `200`:** `{ ...VentaResponse }`

---

### `GET /api/ventas/v1`

Lista todas las ventas activas.

**Response `200`:** `[ ...VentaResponse ]`

---

### `GET /api/ventas/v1/usuario/{idUsuario}`

Filtra por usuario.

**Response `200`:** `[ ...VentaResponse ]`

---

### `GET /api/ventas/v1/fecha`

Filtra por rango de fechas.

**Query params:** `desde=2026-01-01T00:00:00&hasta=2026-12-31T23:59:59`

**Response `200`:** `[ ...VentaResponse ]`

---

### `DELETE /api/ventas/v1/{id}`

Anula la venta: la marca como eliminada junto a sus detalles y **devuelve la mercadería al
stock**. Si el producto maneja lotes, repone en cada lote exactamente la cantidad que se le
descontó, incluso cuando el FIFO repartió una línea entre varios. Los movimientos originales
no se borran: la reversa queda registrada como un movimiento `AJUSTE` adicional.

**Solo ADMIN.**

**Response `204`**

**Error `400`:** la venta ya está cobrada — movió plata y puede estar dentro de un corte
cerrado, así que corresponde una devolución, no una anulación

---

### DetalleVentaResponse

```json
{
  "idProducto": "UUID | null",
  "nombre": "string",
  "cantidad": "int",
  "precioUnitario": "float",
  "subtotal": "float",
  "tipo": "PRODUCTO | MANUAL"
}
```

---

## 7. Compras — `/api/compras/v1`

### `POST /api/compras/v1`

Registra una compra. Si el producto maneja lotes, crea automáticamente un `Lote` y registra el movimiento de stock.

Con `pagoEnEfectivo: true` se descuenta de la caja: genera la salida automática en la sesión
abierta (falla con `400` si no hay ninguna). Reemplaza al `idSesion` que antes mandaba el cliente.

**Request:**
```json
{
  "detalle": [
    {
      "idProducto": "UUID",
      "precioUnitario": "float",
      "cantidad": "int",
      "fechaVencimiento": "date (opcional)",
      "numeroLote": "string (opcional)"
    }
  ],
  "idProveedor": "UUID (opcional)",
  "tipoComprobante": "REMITO | FACTURA (opcional)",
  "nroComprobante": "string (opcional)",
  "observaciones": "string (opcional)",
  "pagoEnEfectivo": "boolean (default false)"
}
```

**Response `200`:**
```json
{
  "id": "UUID",
  "fecha": "datetime",
  "total": "float",
  "detalle": [ "...DetalleCompraResponse" ],
  "proveedor": { "...ProveedorResponse" } | null,
  "tipoComprobante": "string | null",
  "nroComprobante": "string | null",
  "observaciones": "string | null"
}
```

---

### `GET /api/compras/v1/{id}`

**Response `200`:** `{ ...CompraResponse }`

---

### `GET /api/compras/v1`

**Response `200`:** `[ ...CompraResponse ]`

---

### `GET /api/compras/v1/usuario/{idUsuario}`

**Response `200`:** `[ ...CompraResponse ]`

---

### `GET /api/compras/v1/fecha`

**Query params:** `desde=...&hasta=...`

**Response `200`:** `[ ...CompraResponse ]`

---

### `DELETE /api/compras/v1/{id}`

Anula la compra: saca del stock lo que había ingresado y, si se pagó por caja, devuelve la
plata al turno con un movimiento de origen `REVERSA`. Los lotes que quedan en cero se dan de
baja.

**Solo ADMIN.**

**Response `204`**

**Errores `400`:** ya se vendió parte de la mercadería ingresada · la compra se pagó por caja
y ese turno ya cerró su corte (o no hay ninguno abierto)

---

### DetalleCompraResponse

```json
{
  "idCompra": "UUID",
  "idProducto": "UUID",
  "nombreProducto": "string",
  "barcode": "string",
  "cantidad": "int",
  "precioUnitario": "float",
  "total": "float"
}
```

---

## 8. Caja — `/api/caja/v1`

Módulo unificado de caja: sesiones, movimientos manuales, resumen diario y corte.

### `POST /api/caja/v1/abrir`

Abre una nueva sesión de caja. Valida que no exista otra sesión abierta.

**Request:**
```json
{
  "saldoInicial": "float (>= 0)"
}
```

**Response `200`:**
```json
{
  "id": "UUID",
  "fechaApertura": "datetime",
  "saldoInicial": "float",
  "estado": "ABIERTA",
  "idUsuarioApertura": "UUID"
}
```

**Error `400`:** si ya hay una sesión abierta

---

### `GET /api/caja/v1/sesion-activa`

Obtiene la sesión de caja actualmente abierta.

**Response `200`:** `{ ...SesionCajaResponse }`

**Error `400`:** si no hay sesión abierta

---

### `POST /api/caja/v1/entradas`

Registra un movimiento manual de entrada (ej: "fondo para vuelto").

**Request:**
```json
{
  "monto": "float (>= 0)",
  "motivo": "string (max 255, opcional)"
}
```

**Response `200`:** `{ ...MovimientoCajaResponse }`

---

### `POST /api/caja/v1/salidas`

Registra un movimiento manual de salida (ej: "compra de café para el personal").

**Request:**
```json
{
  "monto": "float (>= 0)",
  "motivo": "string (max 255, opcional)"
}
```

**Response `200`:** `{ ...MovimientoCajaResponse }`

---

### `GET /api/caja/v1/movimientos`

Lista movimientos de caja. Si no se especifica rango, usa la sesión activa.

**Query params:** `?desde=2026-07-12T00:00:00&hasta=2026-07-12T23:59:59`

**Response `200`:**
```json
[
  {
    "id": "UUID",
    "idSesion": "UUID",
    "tipo": "ENTRADA | SALIDA",
    "monto": "float",
    "motivo": "string | null",
    "origen": "VENTA | COMPRA | MANUAL",
    "idReferencia": "UUID | null",
    "fecha": "datetime"
  }
]
```

---

### `GET /api/caja/v1/resumen/sesion`

Estado del turno abierto: es lo que se mira antes de cerrar la caja. Solo cuenta **efectivo**,
que es lo que el cajero tiene para contar. Para ver cuánto se cobró con tarjeta o transferencia
en ese mismo turno, usar [`GET /api/ventas/v1/resumen/sesion/{idSesion}`](#get-apiventasv1resumensesionidsesion).

**Response `200`:** `{ ...ResumenCaja }`

**Error `400`:** no hay ninguna caja abierta

---

### `GET /api/caja/v1/resumen/diario`

Resumen de un día completo, calculado sobre los movimientos de esa fecha. **No requiere que
haya una caja abierta**, así que sirve para consultar días ya cerrados. El saldo inicial es la
suma de los saldos de apertura de las sesiones de ese día.

**Query params:** `?fecha=2026-07-12` (opcional, default hoy)

**Response `200`:** `{ ...ResumenCaja }`

---

### ResumenCaja

```json
{
  "fecha": "date",
  "saldoInicial": "float",
  "totalVentas": "float | null",
  "cantidadVentas": "int | null",
  "totalCompras": "float | null",
  "cantidadCompras": "int | null",
  "totalEntradasManuales": "float | null",
  "totalSalidasManuales": "float | null",
  "saldoEsperado": "float"
}
```

> Los totales son `null` únicamente en cortes cerrados antes de que el desglose se persistiera:
> significa "dato desconocido", que no es lo mismo que 0.

---

### `POST /api/caja/v1/corte`

Realiza el corte de caja: cierra la sesión activa, calcula saldo esperado y diferencia.

**Request:**
```json
{
  "saldoReal": "float (>= 0)",
  "observaciones": "string (max 255, opcional)"
}
```

**Response `200`:**
```json
{
  "id": "UUID",
  "fechaApertura": "datetime",
  "fechaCierre": "datetime",
  "saldoInicial": "float",
  "saldoEsperado": "float",
  "saldoReal": "float",
  "diferencia": "float (saldoReal - saldoEsperado)",
  "observaciones": "string | null",
  "idUsuarioApertura": "UUID",
  "idUsuarioCierre": "UUID",
  "resumen": { "...ResumenCajaResponse" }
}
```

---

### `GET /api/caja/v1/corte/ultimo`

Último corte realizado.

**Response `200`:** `{ ...CorteResponse }`

---

### `GET /api/caja/v1/corte/{id}`

Corte por ID. Valida que la sesión esté cerrada.

**Response `200`:** `{ ...CorteResponse }`

**Error `400`:** si la sesión no está cerrada

---

### `GET /api/caja/v1/corte/historial`

El desglose (`resumen`) de cada corte queda congelado al cerrarlo, no se recalcula.

Historial de todos los cortes realizados.

**Response `200`:** `[ ...CorteResponse ]`

---

## 9. Inventario — `/api/inventario/v1`

### `POST /api/inventario/v1/stock`

Crea registro de stock para un producto.

**Request:**
```json
{
  "idProducto": "UUID",
  "cantidad": "int"
}
```

**Response `200`:** `{ "idProducto": "UUID", "cantidad": "int" }`

---

### `GET /api/inventario/v1/stock/{idProducto}`

Stock actual de un producto.

**Response `200`:** `{ "idProducto": "UUID", "cantidad": "int" }`

**Error `404`:** sin stock registrado

---

### `PUT /api/inventario/v1/stock/aumentar`

Incrementa stock. Solo para productos que NO manejan lotes.

**Request:**
```json
{
  "idProducto": "UUID",
  "cantidad": "int",
  "tipo": "COMPRA | VENTA | AJUSTE | MERMA",
  "motivo": "string",
  "idUsuario": "UUID"
}
```

**Response `200`:** stock actualizado

---

### `PUT /api/inventario/v1/stock/disminuir`

Reduce stock. Valida stock suficiente.

**Request:** mismo body que aumentar

**Response `200`:** stock actualizado

**Error `400`:** `"Stock insuficiente. Disponible: X, solicitado: Y"`

---

### `DELETE /api/inventario/v1/stock/{idProducto}`

Soft delete.

**Response `204`**

---

### `POST /api/inventario/v1/controlar`

Ajuste físico de stock. Registra la diferencia como movimiento `AJUSTE`.

**Request:**
```json
{
  "idProducto": "UUID",
  "stockReal": "int",
  "tipo": "string (ignorado)",
  "motivo": "string (opcional)"
}
```

**Response `200`**

---

### `GET /api/inventario/v1/movimientos/{idProducto}`

Historial de movimientos de stock de un producto.

**Response `200`:**
```json
[
  {
    "id": "UUID",
    "idProducto": "UUID",
    "cantidad": "int",
    "tipo": "COMPRA | VENTA | AJUSTE | MERMA",
    "motivo": "string",
    "fecha": "datetime"
  }
]
```

---

### `POST /api/inventario/v1/lotes`

Crea un lote. Valida que `producto.manejaLotes == true`.

**Request:**
```json
{
  "idProducto": "UUID",
  "numeroLote": "string",
  "fechaVencimiento": "date",
  "cantidad": "int"
}
```

**Response `200`:** `{ ...LoteResponse }`

---

### `GET /api/inventario/v1/lotes`

Todos los lotes activos con estado recalculado.

**Response `200`:** `[ ...LoteResponse ]`

---

### `GET /api/inventario/v1/lotes/estado/{estado}`

Filtra por estado: `VIGENTE`, `PROXIMO`, `VENCIDO`, `SIN_FECHA`.

**Response `200`:** `[ ...LoteResponse ]`

---

### `GET /api/inventario/v1/lotes/vencimiento/proximos`
### `GET /api/inventario/v1/lotes/vencimiento/vencidos`
### `GET /api/inventario/v1/lotes/vencimiento/vigentes`

Shorthands para filtrar por estado.

**Response `200`:** `[ ...LoteResponse ]`

---

### LoteResponse

```json
{
  "id": "UUID",
  "idProducto": "UUID",
  "nombreProducto": "string",
  "numeroLote": "string",
  "fechaVencimiento": "date",
  "cantidad": "int",
  "estado": "SIN_FECHA | VENCIDO | PROXIMO | VIGENTE"
}
```

---

## 10. Reportes — `/api/reportes/v1`

**Solo ADMIN.**

> Todos los reportes de dinero usan la misma fuente: **ventas cobradas, filtradas por fecha de
> cobro**, con rangos sin solapamiento entre días consecutivos. `/reportes/ventas`,
> `/reportes/ganancias` y `/ventas/resumen/diario` devuelven el mismo total para el mismo rango.

### `GET /api/reportes/v1/ventas`

Reporte de ventas por día en un rango de fechas.

**Query params:** `desde=2026-07-01&hasta=2026-07-12`

**Response `200`:**
```json
{
  "desde": "date",
  "hasta": "date",
  "totalTransacciones": "int",
  "totalIngresos": "float",
  "porDia": [
    {
      "fecha": "date",
      "cantidad": "int",
      "total": "float"
    }
  ]
}
```

---

### `GET /api/reportes/v1/ganancias`

Ganancia del período, calculada como **margen sobre lo vendido y cobrado**:
`gananciaBruta = totalVentas - costoMercaderiaVendida`, usando el costo congelado en cada línea
de venta al momento de venderla.

`totalCompras` se informa aparte y **no entra en el cálculo**: es flujo de caja. Restarlo daría
pérdida cada vez que se repone mercadería, aunque el negocio haya ganado plata.

`unidadesSinCosto` cuenta las unidades vendidas sin costo conocido (ítems manuales o productos
sin costo cargado). Si es alto, la ganancia informada está sobrestimada.

**Query params:** `desde=2026-07-01&hasta=2026-07-12`

**Response `200`:**
```json
{
  "desde": "date",
  "hasta": "date",
  "totalVentas": "float",
  "costoMercaderiaVendida": "float",
  "gananciaBruta": "float",
  "totalCompras": "float",
  "unidadesSinCosto": "int",
  "porDia": [
    {
      "fecha": "date",
      "ventas": "float",
      "costo": "float",
      "ganancia": "float",
      "compras": "float"
    }
  ]
}
```

---

### `GET /api/reportes/v1/inventario`

Stock actual de todos los productos. Para los productos que manejan lotes, `stockActual` es la
suma de sus lotes activos (antes salía siempre en 0, porque solo se miraba la tabla `stock`).

**Response `200`:**
```json
[
  {
    "idProducto": "UUID",
    "nombre": "string",
    "barcode": "string",
    "stockActual": "int",
    "precio": "float",
    "costo": "float | null",
    "categoria": { "...CategoriaResponse" } | null,
    "manejaLotes": "boolean"
  }
]
```

---

### `GET /api/reportes/v1/productos-mas-vendidos`

Top N productos más vendidos en un período.

**Query params:** `desde=2026-07-01&hasta=2026-07-12&limite=10` (limite default 10)

**Response `200`:**
```json
[
  {
    "idProducto": "UUID",
    "nombre": "string",
    "barcode": "string",
    "cantidadVendida": "int",
    "totalVendido": "float"
  }
]
```
