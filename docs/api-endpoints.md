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
| ------ | ---------------------------------- |
| `400` | `BadRequestException` o validación |
| `401` | `UnauthorizedException` |
| `404` | `ResourceNotFoundException` |
| `500` | Error interno del servidor |

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
- **Header `idUsuario`:** los endpoints que requieren identificar al usuario autenticado lo reciben como header `idUsuario: UUID` (no se extrae del JWT)
- **Swagger UI:** `/swagger-ui/index.html`
- **OpenAPI spec:** `/v3/api-docs`

---

## 1. Auth — `/api/auth/v1`

Todas públicas (no requieren token).

### `POST /api/auth/v1/register`

Registra un nuevo usuario. Se crea con `enabled: false`.

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

**Response `200`:**
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string",
  "usuario": { "...UsuarioResponse" }
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

**Response `200`:** igual que register

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

Revoca el refresh token.

**Header:** `Authorization: Bearer <refreshToken>`

**Response `204`**

---

### `POST /api/auth/v1/verify-email`

**Request:**
```json
{ "token": "string" }
```

**Response `200`**

---

### `POST /api/auth/v1/password-reset`

Solicita reseteo de contraseña. Genera un token (no envía email aún).

**Request:**
```json
{ "username": "string (email del usuario)" }
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

### `DELETE /api/users/v1/{id}`

Soft delete.

**Response `204`**

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
  "rol": "ADMIN | EMPLEADO",
  "enabled": "boolean",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

---

## 3. Productos — `/api/productos/v1`

### `POST /api/productos/v1`

**Header:** `idUsuario: UUID`

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
  ],
  "idSesion": "UUID (opcional)"
}
```

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

**Error `400`:** stock insuficiente

---

### `POST /api/ventas/v1/{id}/cobrar`

Marca una venta como cobrada. Si tiene `idSesion`, registra entrada automática en caja.

**Header:** `idUsuario: UUID`

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
  "cambio": "float (montoRecibido - total)"
}
```

**Error `400`:** si ya está cobrada o monto recibido < total

---

### `GET /api/ventas/v1/resumen/diario`

Resumen de ventas cobradas del día.

**Query params:** `?fecha=2026-07-12` (opcional, default hoy)

**Response `200`:**
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

## 7. Compras — `/api/compras/v1`

### `POST /api/compras/v1`

Registra una compra. Si el producto maneja lotes, crea automáticamente un `Lote` y registra el movimiento de stock. Si tiene `idSesion`, registra salida automática en caja.

**Header:** `idUsuario: UUID`

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
  "idSesion": "UUID (opcional)"
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

Soft delete.

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

## 8. Caja — `/api/caja/v1`

Módulo unificado de caja: sesiones, movimientos manuales, resumen diario y corte.

### `POST /api/caja/v1/abrir`

Abre una nueva sesión de caja. Valida que no exista otra sesión abierta.

**Header:** `idUsuario: UUID`

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

**Header:** `idUsuario: UUID`

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

**Header:** `idUsuario: UUID`

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

### `GET /api/caja/v1/resumen/diario`

Resumen completo de la sesión activa (ventas, compras, movimientos manuales, saldo esperado).

**Query params:** `?fecha=2026-07-12` (opcional, default hoy)

**Response `200`:**
```json
{
  "fecha": "date",
  "saldoInicial": "float",
  "totalVentas": "float",
  "cantidadVentas": "int",
  "totalCompras": "float",
  "cantidadCompras": "int",
  "totalEntradasManuales": "float",
  "totalSalidasManuales": "float",
  "saldoEsperado": "float"
}
```

---

### `POST /api/caja/v1/corte`

Realiza el corte de caja: cierra la sesión activa, calcula saldo esperado y diferencia.

**Header:** `idUsuario: UUID`

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

**Header:** `idUsuario: UUID`

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

Reporte de ganancias (ventas - compras) por día.

**Query params:** `desde=2026-07-01&hasta=2026-07-12`

**Response `200`:**
```json
{
  "desde": "date",
  "hasta": "date",
  "totalVentas": "float",
  "totalCompras": "float",
  "gananciaBruta": "float",
  "porDia": [
    {
      "fecha": "date",
      "ventas": "float",
      "compras": "float",
      "ganancia": "float"
    }
  ]
}
```

---

### `GET /api/reportes/v1/inventario`

Stock actual de todos los productos.

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
