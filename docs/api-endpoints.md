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
| Invitar, bloquear, dar de baja y restaurar a un EMPLEADO | ✅ | ✅ | ❌ |
| Invitar, bloquear, dar de baja y restaurar a un ADMIN | ✅ | ❌ | ❌ |
| Promover o degradar entre ADMIN y EMPLEADO | ✅ | ❌ | ❌ |
| Invitación, bloqueo, baja o asignación del rol SUPERADMIN | ❌ | ❌ | ❌ |

Nadie gestiona a un usuario de su mismo nivel ni de uno superior, y **nadie se bloquea ni se
borra a sí mismo**. De ahí que no exista alta de SUPERADMIN por API: la llave maestra sale del
seed de la base (`01_seed.sql`). Si el alta de superadmins fuera
un endpoint, tomar una sesión de superadmin alcanzaría para fabricarse otro.

Cambiar la contraseña exige conocer la actual, así que **ni el ADMIN ni el SUPERADMIN pueden
hacerlo por otro usuario**: para eso está el flujo de reseteo.

---

## 1. Auth — `/api/auth/v1`

Todas públicas (no requieren token).

> **No hay autorregistro ni alta directa.** La única forma de entrar al sistema es que un
> administrador invite ([`POST /api/users/v1/invitaciones`](#post-apiusersv1invitaciones)) y que
> la persona cierre el alta desde el mail. Nadie se da de alta solo en el sistema de un
> comercio, y quien invita nunca conoce la contraseña de quien invitó.

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

### `GET /api/auth/v1/invitacion?token=…`

Datos de la invitación, para saludar a la persona y precargarle el nombre de usuario sugerido.
**Público**, por la misma razón que el `POST` de abajo. **No consume el token**: sirve además
para distinguir un enlace vencido antes de hacer llenar el formulario.

**Response `200`**
```json
{
  "nombre": "string",
  "apellido": "string",
  "email": "string",
  "usernameSugerido": "string"
}
```

`400` si el token es inválido, expirado o ya usado, o si la cuenta dejó de estar `PENDIENTE`:
se bloqueó, se dio de baja o **ya se activó** desde que se envió la invitación.

---

### `POST /api/auth/v1/invitacion/aceptar`

Cierre del alta por invitación: define la contraseña —y opcionalmente el nombre de usuario— y
pasa la cuenta a `ACTIVO`. **Público** — quien la acepta todavía no tiene contraseña, su
credencial es el token del mail.

**Request:**
```json
{
  "token": "string (el del enlace del mail)",
  "password": "string (min 8, max 72)",
  "username": "string (opcional, min 1, max 50, sin @)"
}
```

Sin `username` queda el que se derivó del email al invitar — el mismo que devuelve el `GET` de
arriba como `usernameSugerido`.

**Response `200`** — después hay que loguearse normalmente.

**Errores `400`:** token inválido, vencido o ya usado · token de otro tipo · la cuenta dejó de
estar `PENDIENTE` entre la invitación y la aceptación —bloqueada, dada de baja o ya activada por
el reseteo de contraseña— · el `username` elegido ya está tomado por otra cuenta

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

> Si la cuenta todavía estaba `PENDIENTE`, este endpoint además **la activa**: el token viajó al
> email de la cuenta, la misma prueba de identidad que pide la invitación. Sin eso, quien
> resetea en vez de aceptar la invitación se quedaría con una contraseña válida y sin poder
> entrar nunca. Una cuenta **bloqueada** no se destraba por acá.

**Errores:** `400` token inválido, vencido, ya usado o de otro tipo · `404` el usuario ya no
existe

---

## 2. Usuarios — `/api/users/v1`

### `POST /api/users/v1/invitaciones`

**Alta por invitación — el flujo recomendado.** Crea la cuenta en estado `PENDIENTE` y le manda
un mail a la persona con un enlace para que **defina su propia contraseña**. Quien invita nunca
conoce la contraseña del invitado.

**ADMIN o SUPERADMIN**. Es la **única** alta del sistema. Solo se invita por debajo del propio
nivel: el SUPERADMIN invita ADMIN y EMPLEADO, el ADMIN solo EMPLEADO.

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

**Errores:** `400` email ocupado (ver abajo) o username explícito en uso · `403` sin rol ADMIN o
rol pedido no permitido · `502` el mail no se pudo enviar

Un email ya tomado devuelve `400` con un mensaje que dice **qué pasa con ese email y qué hacer
al respecto**:

| Estado de la cuenta que lo ocupa | Mensaje | Salida |
|---|---|---|
| Dada de baja | `"El email pertenece a una cuenta dada de baja: restaurala para volver a darle acceso"` | [`POST /{id}/restaurar`](#post-apiusersv1idrestaurar) |
| `PENDIENTE` | `"Ese email ya tiene una invitación pendiente: reenviásela en lugar de invitarlo de nuevo"` | [`POST /{id}/invitaciones/reenviar`](#post-apiusersv1idinvitacionesreenviar) |
| `ACTIVO` o `BLOQUEADO` | `"El email ya está registrado"` | ninguna: la persona ya está en el sistema |

Las dos primeras existen porque el email y el username de una cuenta dada de baja **siguen
ocupados** —la baja es lógica y las unique keys no miran `deleted_at`—, y porque reinvitar a
alguien que ya tiene una invitación en curso es reenviar, no dar de alta otra vez. El id para
esos dos endpoints sale de `GET /api/users/v1?incluirBajas=true`.

> **La salida se ofrece solo si la cuenta está por debajo del nivel de quien invita**, porque
> restaurar y reenviar exigen esa misma jerarquía. Un ADMIN que tropieza con la cuenta de otro
> ADMIN lee el mensaje cortado —`"El email pertenece a una cuenta dada de baja"`, sin el
> `": restaurala…"`—: el hecho le sirve para entender por qué el email está tomado, pero la
> instrucción lo mandaría a un `403`.

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

### `GET /api/users/v1/me`

Perfil del usuario autenticado.

**Response `200`:** `{ ...UsuarioResponse }`

---

### `GET /api/users/v1/{id}`

**Response `200`:** `{ ...UsuarioResponse }`

**Error `404`:** si no existe o fue eliminado

---

### `GET /api/users/v1?incluirBajas=false`

Lista los usuarios. Por defecto solo los que están en pie; con `incluirBajas=true` suma las
cuentas dadas de baja, que vienen con `deletedAt` cargado.

Es cómo el front encuentra una cuenta para
[restaurarla](#post-apiusersv1idrestaurar): en el listado normal no aparecen.

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

### `POST /api/users/v1/{id}/restaurar`

**Reactiva una cuenta dada de baja.** Vuelve como `PENDIENTE`, con la contraseña anterior
invalidada, y le llega una **invitación nueva** para que defina otra. Es la contracara del
`DELETE`.

Restaura la fila original en vez de crear una cuenta nueva: el id del usuario lo referencian
`ventas`, `compras`, `movimientos_caja`, `movimientos_stock` y `sesiones_caja` con `ON DELETE
RESTRICT`, así que un alta nueva con el mismo email partiría su historial en dos. Conserva
además su rol: la restauración devuelve el acceso, no lo redefine.

No puede chocar con nada: el email y el username siguieron reservados durante toda la baja,
porque las unique keys de la tabla no miran `deleted_at`.

**Mismas reglas de jerarquía que `bloquear` y el `DELETE`**: se restaura por debajo del propio
nivel. El id sale de `GET /api/users/v1?incluirBajas=true`.

**Response `200`:** `{ ...UsuarioResponse }` con `estado: "PENDIENTE"` y `deletedAt: null`

**Errores:** `400` la cuenta no está dada de baja · `403` el objetivo no está por debajo tuyo ·
`404` no existe · `502` el mail no se pudo enviar

> Si el envío falla, **la restauración se revierte** y la cuenta sigue dada de baja: revivirla
> sin que nadie pueda entrar es peor que dejarla como estaba.

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
  "updatedAt": "datetime",
  "deletedAt": "datetime | null"
}
```

`deletedAt` viene con valor **solo** en `GET /api/users/v1?incluirBajas=true`, que es el único
endpoint que devuelve cuentas dadas de baja.

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

**Error `400`:** si el barcode ya existe, o la categoría/proveedor no existen (o están dados de baja)

---

### `GET /api/productos/v1`

Lista paginada de productos activos. Todos los filtros son opcionales y se combinan entre sí.

**Query params:** `?q=texto&categoria=UUID&proveedor=UUID&page=0&size=20`

`page` arranca en 0; `size` va de 1 a 100. Orden: `updatedAt` descendente, con el `id` como
desempate para que la paginación sea estable.

**Response `200`:** `Page<ProductoResponse>` (`content`, `totalElements`, `totalPages`, `number`, `size`)

**Error `400`:** `page` negativo, o `size` fuera de 1..100

---

### `GET /api/productos/v1/{id}`

**Response `200`:** `{ ...ProductoResponse }`

---

### `GET /api/productos/v1/barcode/{barcode}`

Busca por código de barras.

**Response `200`:** `{ ...ProductoResponse }`

---

### `GET /api/productos/v1/search`

Búsqueda por nombre (case-insensitive). Equivale a `GET /api/productos/v1?q=texto`.

**Query params:** `?q=texto&page=0&size=20`

**Response `200`:** `Page<ProductoResponse>`

**Error `400`:** `page` negativo, o `size` fuera de 1..100

---

### `PUT /api/productos/v1/{id}`

Reemplazo total: el body es la representación completa del producto. Los campos opcionales que
se omitan o vengan en `null` **se borran** (es la forma de desasignar categoría, proveedor,
costo o margen).

**Request:** mismo body que POST

**Response `200`:** `{ ...ProductoResponse }`

**Error `404`:** el producto no existe o está dado de baja

---

### `DELETE /api/productos/v1/{id}`

Soft delete. Da de baja también la fila de stock y los lotes del producto.

**Response `204`**

**Error `400`:** el producto todavía tiene existencias (stock o lotes con unidades). Hay que
descargarlas antes, con una venta o con un ajuste de stock.

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
  "nombre": "string (max 100, único entre las categorías activas)",
  "descripcion": "string (max 255, opcional)"
}
```

`nombre` y `descripcion` se recortan antes de guardarse. El nombre de una categoría dada de baja
queda liberado: se puede volver a crear una con ese mismo nombre.

**Response `200`:** `{ ...CategoriaResponse }`

**Error `400`:** ya hay una categoría activa con ese nombre

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

Soft delete. Libera el nombre para una categoría nueva.

**Response `204`**

**Error `400`:** hay productos activos asignados a la categoría. Hay que reasignarlos antes.

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
  "email": "string (max 100, opcional, formato email)",
  "direccion": "string (max 255, opcional)"
}
```

Los campos se recortan antes de guardarse.

**Response `200`:** `{ ...ProveedorResponse }`

**Error `400`:** ya hay un proveedor activo con ese nombre, o el email no tiene formato válido

---

### `GET /api/proveedores/v1`

**Query params:** `?incluirBajas=false`

Con `incluirBajas=true` suma los proveedores dados de baja, que vienen con `deletedAt` cargado.
Es cómo el front encuentra el que hay que restaurar: en el listado normal no aparecen.

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

Baja lógica. El proveedor deja de poder usarse en compras nuevas y de asignarse a un producto,
pero **sigue apareciendo en el historial de compras y en los productos que lo tenían**, con
`deletedAt` cargado. Es reversible con `/restaurar`. No exige que no tenga compras ni productos:
conservar esa referencia es justamente el punto.

**Response `204`**

---

### `POST /api/proveedores/v1/{id}/restaurar`

Vuelve a habilitar un proveedor dado de baja. Va sobre la fila original y no sobre un alta nueva,
para no partir el historial de compras, que lo referencia por id. Solo ADMIN.

**Response `200`:** `{ ...ProveedorResponse }` (con `deletedAt` en `null`)

**Error `400`:** el proveedor no está dado de baja, o mientras tanto se dio de alta otro activo
con el mismo nombre

---

### ProveedorResponse

```json
{
  "id": "UUID",
  "nombre": "string",
  "telefono": "string | null",
  "email": "string | null",
  "direccion": "string | null",
  "deletedAt": "datetime | null"
}
```

`deletedAt` viene con valor solo cuando el proveedor está dado de baja: desde
`GET /api/proveedores/v1?incluirBajas=true`, desde el historial de compras y desde el catálogo
de productos.

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
  "tipoComprobante": "REMITO | FACTURA (opcional, se guarda en mayúsculas)",
  "nroComprobante": "string (opcional, único por proveedor y tipo)",
  "observaciones": "string (opcional)",
  "pagoEnEfectivo": "boolean (default false)"
}
```

Si el producto maneja lotes, la línea **debe traer `fechaVencimiento`**: el lote se da de alta
por el módulo de inventario, que la exige. Sin ella el lote quedaría en estado `SIN_FECHA` y no
aparecería en ningún control de vencimientos.

`tipoComprobante` se recorta y se guarda en mayúsculas, y el filtro del listado busca con el
mismo criterio: cargar `"factura"` y filtrar por `"FACTURA"` encuentra la compra.

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

**Errores `400`:** el proveedor no existe o está dado de baja · algún producto no existe · **ese
proveedor ya tiene una compra con ese número dentro del mismo tipo de comprobante** · falta la fecha de vencimiento en
una línea de un producto que maneja lotes · no hay turno de caja abierto y se marcó
`pagoEnEfectivo`

> El número de comprobante es único **por proveedor y por tipo**: dos proveedores distintos
> pueden emitir el mismo número, y un mismo proveedor puede tener un remito y una factura con el
> mismo número, porque cada tipo lleva su propia numeración. Las compras sin proveedor o sin
> número quedan fuera de la regla, y anular una compra libera su número para volver a cargarla.

---

### `GET /api/compras/v1/{id}`

**Response `200`:** `{ ...CompraResponse }`

---

### `GET /api/compras/v1`

Listado paginado con todos los filtros opcionales y combinables. Reemplaza a los endpoints
dedicados por usuario y por fecha, que ya no existen.

**Query params:** `?proveedor=UUID&tipoComprobante=FACTURA&desde=...&hasta=...&sortTotal=asc|desc&page=0&size=20`

El rango de fechas es **semiabierto**: incluye `desde` y excluye `hasta`, igual que en ventas y
en los reportes. `page` arranca en 0; `size` va de 1 a 100. Por defecto ordena por fecha
descendente; con `sortTotal` ordena por importe. En los dos casos desempata por `id`, para que la
paginación sea estable cuando varias compras comparten fecha o importe.

**Response `200`:** `Page<CompraResponse>` (`content`, `totalElements`, `totalPages`, `number`,
`size`)

**Error `400`:** `page` negativo, o `size` fuera de 1..100

---

### `DELETE /api/compras/v1/{id}`

Anula la compra: saca del stock lo que había ingresado y, si se pagó por caja, devuelve la
plata al turno con un movimiento de origen `REVERSA`. Los lotes que quedan en cero se dan de
baja.

**No revierte el costo, el margen, el precio ni el proveedor que el alta le escribió al
producto.** Una compra se anula por muchos motivos y en ninguno el precio de venta vigente tiene
por qué volver atrás; si lo que estaba mal era el precio, se corrige con
`PUT /api/productos/v1/{id}`.

**Solo ADMIN.**

**Response `204`**

**Errores `400`:** ya se vendió parte de la mercadería ingresada —el mensaje nombra el producto—
· la compra se pagó por caja y ese turno ya cerró su corte (o no hay ninguno abierto)

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

> Las operaciones que escriben cantidades (aumentar, disminuir, ajuste manual, venta, compra y
> sus anulaciones) toman el lock de la fila de stock o de lote sobre la que trabajan, para que
> dos pedidos simultáneos sobre el mismo producto no partan del mismo valor. Los locks se toman
> siempre en el mismo orden —productos por id ascendente y, dentro de cada uno, sus lotes por
> vencimiento— así que dos operaciones no pueden trabarse cruzadas. Si aun así la base corta
> por espera de lock, la respuesta es **`409`** y el pedido se puede reintentar tal cual.
>
> El detalle de una venta o una compra se guarda y se devuelve en el orden en que se cargó: el
> orden de bloqueo es interno y no cambia el comprobante.

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

> `tipo` solo acepta **`AJUSTE`** o **`MERMA`**: `COMPRA` y `VENTA` los escribe el sistema al
> registrar el comprobante, y son los que lee la reversa de una anulación. `idReferencia` que
> venga en el body se descarta, y `idUsuario` sale siempre del JWT.

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

> Mismas reglas de `tipo`, `idReferencia` e `idUsuario` que `aumentar`.

Reduce stock. Valida stock suficiente.

**Request:** mismo body que aumentar

**Response `200`:** stock actualizado

**Error `400`:** `"Stock insuficiente. Disponible: X, solicitado: Y"`

---

### `DELETE /api/inventario/v1/stock/{idProducto}`

Soft delete de la fila de stock. **Solo ADMIN**: borrar el stock de un producto saltea las
validaciones de la baja de producto y puede tapar un faltante sin dejar rastro operativo.

**Response `204`**

**Error `400`:** el producto todavía tiene existencias. Hay que ajustar el stock a 0 antes.

**Error `403`:** sin rol ADMIN

---

### `POST /api/inventario/v1/controlar`

Ajuste físico de stock. Registra la diferencia como movimiento `AJUSTE`. **Solo ADMIN**: es la
operación que puede hacer desaparecer un faltante, así que va con el resto de lo sensible.

Si el producto todavía no tiene fila de stock, la crea: un producto sin fila es stock 0, igual
que en `GET /stock/{idProducto}`. **No aplica a productos que manejan lotes**, cuya existencia
es la suma de sus lotes.

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

**Error `400`:** el producto maneja lotes, o el stock real es negativo

**Error `403`:** sin rol ADMIN

---

### `GET /api/inventario/v1/movimientos/{idProducto}`

Historial de movimientos de stock de un producto, del más reciente al más viejo.

**Query params:** `?page=0&size=20`

`page` arranca en 0; `size` va de 1 a 100.

**Error `400`:** `page` negativo, o `size` fuera de 1..100

**Response `200`:**
```json
`Page<MovimientoStockResponse>` (`content`, `totalElements`, `totalPages`, `number`, `size`),
donde cada elemento es:

```json
{
  "id": "UUID",
  "idProducto": "UUID",
  "cantidad": "int",
  "tipo": "COMPRA | VENTA | AJUSTE | MERMA",
  "motivo": "string",
  "fecha": "datetime",
  "idLote": "UUID | null",
  "idReferencia": "UUID | null",
  "idUsuario": "UUID | null"
}
```

`idLote` dice de qué lote salió la unidad (null si el producto no maneja lotes), `idReferencia`
es la venta o compra que originó el movimiento, y `idUsuario` quién lo cargó.

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
