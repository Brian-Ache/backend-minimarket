# API Endpoints — MiniMarket

Base URL: `http://localhost:8080` (configurable vía `SERVER_PORT`)

---

## Formato de errores común

Todas las respuestas de error siguen esta estructura:

```json
{
  "error": "mensaje de error",
  "timestamp": "2026-07-12T15:00:00"
}
```

| Status | Causa                              |
| ------ | ---------------------------------- |
| `400`  | `BadRequestException` o validación |
| `401`  | `UnauthorizedException`            |
| `404`  | `ResourceNotFoundException`        |
| `500`  | Error interno del servidor         |

Errores de validación (`400`):

```json
{
  "error": "Validation failed",
  "details": ["El campo no puede estar vacío"],
  "timestamp": "2026-07-12T15:00:00"
}
```

---

## 1. Auth — `/api/auth/v1`

Todas las rutas de auth son públicas (no requieren token).

### `POST /api/auth/v1/register`

Registra un nuevo usuario. El usuario se crea con `enabled: false`.

**Request:**

```json
{
  "nombre": "string (max 50)",
  "apellido": "string (max 50)",
  "email": "email (max 100)",
  "username": "string (max 50)",
  "password": "string (min 8, max 72)"
}
```

**Response `201`:**

```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string",
  "usuario": { ...UsuarioResponse }
}
```

---

### `POST /api/auth/v1/login`

**Request:**

```json
{
  "username": "string",
  "password": "string"
}
```

**Response `200`:** igual que register (accessToken + refreshToken + usuario)

---

### `POST /api/auth/v1/refresh`

Rota el refresh token (invalida el anterior, genera uno nuevo).

**Request:**

```json
{
  "refreshToken": "string"
}
```

**Response `200`:** idem, nuevo accessToken + refreshToken rotado

---

### `POST /api/auth/v1/logout`

Revoca el refresh token.

**Header:** `Authorization: Bearer <refreshToken>`

**Response `204`:** sin body

---

### `POST /api/auth/v1/verify-email`

**Request:**

```json
{
  "token": "string"
}
```

**Response `200`**

---

### `POST /api/auth/v1/password-reset`

Solicita reseteo de contraseña. Genera un token de reseteo (no envía email aún).

**Request:**

```json
{
  "username": "string (email del usuario)"
}
```

**Response `200`**

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

Requieren autenticación (header `Authorization: Bearer <token>`).

### `GET /api/users/v1/me`

Obtiene el perfil del usuario autenticado.

**Response `200`:** `{ ...UsuarioResponse }`

---

### `GET /api/users/v1/{id}`

**Response `200`:** `{ ...UsuarioResponse }`

**Error `404`:** si el usuario no existe o fue eliminado

---

### `GET /api/users/v1`

Lista todos los usuarios activos (no eliminados).

**Response `200`:** `[ ...UsuarioResponse ]`

---

### `PATCH /api/users/v1/{id}`

Actualiza nombre y/o apellido del usuario.

**Request:**

```json
{
  "nombre": "string (max 50, opcional)",
  "apellido": "string (max 50, opcional)"
}
```

**Response `200`:** `{ ...UsuarioResponse }`

---

### `DELETE /api/users/v1/{id}`

Soft delete del usuario.

**Response `204`**

---

### `POST /api/users/v1/{id}/change-password`

Cambia la contraseña del usuario.

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

### UsuarioResponse (formato compartido)

```json
{
  "id": "UUID",
  "nombre": "string",
  "apellido": "string",
  "username": "string",
  "email": "string",
  "rol": "ADMIN | EMPLEADO",
  "enabled": "boolean",
  "createdAt": "datetime (ISO 8601)",
  "updatedAt": "datetime (ISO 8601)"
}
```

---

## 3. Productos — `/api/productos/v1`

Requieren autenticación.

### `POST /api/productos/v1`

Crea un producto.

**Header:** `idUsuario: UUID`

**Request:**

```json
{
  "nombre": "string (max 200)",
  "barcode": "string (max 100, único)",
  "precio": "float (>= 0)",
  "manejaLotes": "boolean"
}
```

**Response `200`:** `{ ...ProductoResponse }`

**Error `400`:** si el barcode ya existe

---

### `GET /api/productos/v1`

Lista todos los productos activos.

**Response `200`:** `[ ...ProductoResponse ]`

---

### `GET /api/productos/v1/{id}`

**Response `200`:** `{ ...ProductoResponse }`

---

### `GET /api/productos/v1/barcode/{barcode}`

Busca por código de barras.

**Response `200`:** `{ ...ProductoResponse }`

---

### `PUT /api/productos/v1/{id}`

Actualiza un producto.

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
  "manejaLotes": "boolean"
}
```

---

## 4. Ventas — `/api/ventas/v1`

Requieren autenticación.

### `POST /api/ventas/v1`

Registra una venta con sus detalles. Si el producto maneja lotes, descuenta automáticamente del lote más próximo a vencer (FIFO). Si no maneja lotes, descuenta del stock global.

**Header:** `idUsuario: UUID`

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

**Response `200`:**

```json
{
  "id": "UUID",
  "fecha": "datetime",
  "total": "float",
  "detalles": [ ...DetalleVentaResponse ]
}
```

**Error `400`:** si no hay stock suficiente (global o en lotes)

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

Soft delete de la venta y sus detalles.

**Response `204`**

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

## 5. Compras — `/api/compras/v1`

Requieren autenticación.

### `POST /api/compras/v1`

Registra una compra. Si el producto maneja lotes, crea automáticamente un `Lote` y registra el movimiento de stock asociado. Los campos `fechaVencimiento` y `numeroLote` son opcionales pero necesarios para productos con lotes.

**Header:** `idUsuario: UUID`

**Request:**

```json
{
  "detalle": [
    {
      "idProducto": "UUID",
      "precioUnitario": "float",
      "cantidad": "int",
      "fechaVencimiento": "date (YYYY-MM-DD, opcional)",
      "numeroLote": "string (opcional)"
    }
  ]
}
```

**Response `200`:**

```json
{
  "id": "UUID",
  "fecha": "datetime",
  "total": "float",
  "detalle": [ ...DetalleCompraResponse ]
}
```

---

### `GET /api/compras/v1/{id}`

**Response `200`:** `{ ...CompraResponse }`

---

### `GET /api/compras/v1`

Lista todas las compras activas.

**Response `200`:** `[ ...CompraResponse ]`

---

### `GET /api/compras/v1/usuario/{idUsuario}`

Filtra por usuario.

**Response `200`:** `[ ...CompraResponse ]`

---

### `GET /api/compras/v1/fecha`

Filtra por rango de fechas.

**Query params:** `desde=...&hasta=...`

**Response `200`:** `[ ...CompraResponse ]`

---

### `DELETE /api/compras/v1/{id}`

Soft delete de la compra y sus detalles.

**Response `204`**

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

## 6. Inventario — `/api/inventario/v1`

Requieren autenticación.

### `POST /api/inventario/v1/stock`

Crea un registro de stock para un producto.

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

Obtiene el stock actual de un producto.

**Response `200`:** `{ "idProducto": "UUID", "cantidad": "int" }`

**Error `404`:** si el producto no tiene stock registrado

---

### `PUT /api/inventario/v1/stock/aumentar`

Incrementa el stock de un producto. Solo para productos que NO manejan lotes.

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

**Response `200`:** `{ "idProducto": "...", "cantidad": "int" }`

---

### `PUT /api/inventario/v1/stock/disminuir`

Reduce el stock de un producto. Valida stock suficiente.

**Request:** mismo body que aumentar

**Response `200`:** stock actualizado

**Error `400`:** `"Stock insuficiente. Disponible: X, solicitado: Y"`

---

### `DELETE /api/inventario/v1/stock/{idProducto}`

Soft delete del registro de stock.

**Response `204`**

---

### `POST /api/inventario/v1/controlar`

Ajuste físico de stock (control de inventario). Registra la diferencia como movimiento de tipo `AJUSTE`.

**Header:** `idUsuario: UUID`

**Request:**

```json
{
  "idProducto": "UUID",
  "stockReal": "int",
  "tipo": "string (opcional, ignorado)",
  "motivo": "string (opcional)"
}
```

**Response `200`:** `"Stock controlado correctamente"`

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

Crea un lote para un producto que maneja lotes. Valida que `producto.manejaLotes == true`.

**Request:**

```json
{
  "idProducto": "UUID",
  "numeroLote": "string",
  "fechaVencimiento": "date (YYYY-MM-DD)",
  "cantidad": "int"
}
```

**Response `200`:** `{ ...LoteResponse }`

**Error `400`:** si el producto no maneja lotes

---

### `GET /api/inventario/v1/lotes`

Lista todos los lotes activos con estado recalculado.

**Response `200`:** `[ ...LoteResponse ]`

---

### `GET /api/inventario/v1/lotes/estado/{estado}`

Filtra lotes por estado. `estado` puede ser: `VIGENTE`, `PROXIMO`, `VENCIDO`, `SIN_FECHA`.

**Response `200`:** `[ ...LoteResponse ]`

---

### `GET /api/inventario/v1/lotes/vencimiento/proximos`

### `GET /api/inventario/v1/lotes/vencimiento/vencidos`

### `GET /api/inventario/v1/lotes/vencimiento/vigentes`

Shorthands para filtrar por estado sin escribir el enum.

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

## Notas para el frontend

- **Autenticación:** todas las rutas excepto `/api/auth/v1/**`, `/swagger-ui/**`, `/v3/api-docs/**` requieren header `Authorization: Bearer <token>`.
- **IDs:** todos los IDs son UUID v4.
- **Fechas:** se envian/reciben en formato ISO 8601 (`2026-07-12T15:00:00`).
- **Soft delete:** los GET por ID de un registro eliminado responden `404`.
- **Swagger UI:** disponible en `/swagger-ui/index.html`.
- **OpenAPI spec:** disponible en `/v3/api-docs`.
- **CORS:** configurado para los orígenes definidos en variable `CORS_ALLOWED_ORIGINS` (default: `http://localhost:5173`).
