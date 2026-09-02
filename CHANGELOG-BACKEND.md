# Changelog Backend

## 2026-08-27

---

#### CORS
- **Archivo**: `CorsConfig.java`
- Se agregó `http://localhost:1420` a la lista de orígenes permitidos. Esto resolvía el bloqueo del preflight CORS al hacer login desde Tauri (Vite corre en el puerto 1420).

#### Productos — Ordenamiento
- **Archivo**: `ProductoController.java`
- Se agregó `Sort.by(Sort.Direction.DESC, "updatedAt")` a ambos `PageRequest.of()` del endpoint GET `/api/productos/v1`. Ahora los productos se devuelven del más reciente al más antiguo.

#### Compilación — Fix de parámetros
- **Archivos**: `InventarioService.java:175`, `ReporteService.java:116`
- Se corrigió la llamada a `productosApi.getAll()`, que requería un parámetro `PageRequest`. Se agregó `PageRequest.of(0, Integer.MAX_VALUE)` para traer todos los registros. Se agregó el import de `PageRequest` en ambos archivos.

#### Inventario — Endpoint batch de stock
- **Archivos**: `StockRepository.java`, `InventarioApi.java`, `InventarioService.java`, `InventarioController.java`
- Nuevo método `findByIdProductoInAndDeletedAtIsNull(List<UUID> ids)` en `StockRepository` que retorna una lista de `Stock` activos para múltiples productos en una sola query.
- Nueva firma `List<StockResponse> getByIdProductos(List<UUID> ids)` en `InventarioApi`.
- Implementación de `getByIdProductos()` en `InventarioService` — convierte la lista de `Stock` a `StockResponse` (solo `idProducto` y `cantidad`).
- Nuevo endpoint `POST /api/inventario/v1/stock/batch` en `InventarioController` que recibe un `List<UUID>` en el body y devuelve `List<StockResponse`. Esto permite consultar el stock de todos los productos de la tabla en un solo request.

#### Stock — Script de seed
- **Archivo**: `backend/script/database/03_seed_stock.sql` (nuevo)
- 81 variables `@p_XXXX` (una por cada producto del seed).
- 3 INSERT INTO stock (uno por categoría: Bebidas, Almacén, Limpieza).
- Stock IDs siguen el patrón `55555555-5555-4555-8555-555555555XXX`.
- Rangos: Bebidas 8-48, Almacén 30-120, Limpieza 25-60.
- Total: 81 productos, 3,762 unidades, promedio 46.4 por producto.
- Incluye SELECT de verificación al final. Se ejecutó exitosamente contra la DB (container `mysql-minimarket`).

---

## 2026-08-28

---

#### ProductoService — Stock automático al crear producto
- **Archivo**: `modules/productos/service/ProductoService.java`
- **Por qué**: al crear un producto nuevo, no existía un registro en la tabla `stock`. Si luego se hacía una compra de ese producto, el endpoint fallaba con "Stock no encontrado" porque `InventarioService.aumentar()` no encontraba el registro.
- **Qué cambió**: se inyectó `StockRepository` y después de `productoRepository.save(producto)`, se crea automáticamente un registro `Stock` con `cantidad(0)`:
  ```java
  Stock stock = Stock.builder()
      .idProducto(guardado.getId())
      .cantidad(0)
      .build();
  stockRepository.save(stock);
  ```

#### DetalleCompraRequest — Campos margen y precioVenta
- **Archivo**: `modules/compras/api/dto/DetalleCompraRequest.java`
- **Por qué**: el frontend envía `costo`, `margen` y `precio` de cada producto en la compra. Antes solo se enviaba `precioUnitario` (costo), y el `margen` y `precio` se perdían. Si el backend recalculaba el precio podía no coincidir con el del frontend por diferencias de redondeo.
- **Qué cambió**: se agregaron los campos `private Float margen;` (opcional) y `private Float precioVenta;` (opcional).

#### CompraService — Actualiza costo, margen, precio y proveedor del producto
- **Archivo**: `modules/compras/service/CompraService.java`
- **Por qué**: cuando se hacía una compra, el `costo`, `margen` y `precio` del producto no se actualizaban. Además, el proveedor de la compra no se reflejaba en el producto.
- **Qué cambió**:
  - Se inyectó `ProductoRepository` (además de `ProductosApi`).
  - En el loop de `crear()`, se reemplazó `productosApi.getById()` por `productoRepository.findByIdAndDeletedAtIsNull()` para obtener la entidad directamente.
  - Después de obtener el producto, se actualizan sus campos: `costo`, `margen`, `precio` y `idProveedor`.
  - Se usa `precioVenta` directamente del frontend (sin recalcular) para evitar desfases por redondeo.

#### InventarioService — `aumentar()` mantiene validación estricta
- **Archivo**: `modules/inventario/service/InventarioService.java`
- **Qué quedó**: `aumentar()` lanza `ResourceNotFoundException("Stock no encontrado para el producto")` si no hay registro de stock. Esto es correcto porque el flujo normal (producto → crea stock(0) → compra → aumentar) siempre va a encontrar el registro.

#### CompraRepository — Queries paginados
- **Archivo**: `modules/compras/repository/CompraRepository.java`
- **Por qué**: los endpoints de compras (`getAll`, `getByUsuario`, `getByFecha`) retornaban `List<Compra>` sin soporte de paginación.
- **Qué cambió**:
  - Nuevo método `findAllPaginated(Pageable)` con `@Query` que ordena por `createdAt DESC`.
  - `findByCreatedAtBetweenAndDeletedAtIsNull` y `findByIdUsuarioAndDeletedAtIsNull` ahora retornan `Page<Compra>` y aceptan `Pageable`.

#### CompraService — Métodos con paginación
- **Archivo**: `modules/compras/service/CompraService.java`
- `getAll(Pageable)` → `Page<CompraResponse>` (usando `findAllPaginated().map()`).
- `getByUsuario(UUID, Pageable)` → `Page<CompraResponse>`.
- `getByFecha(LocalDateTime, LocalDateTime, Pageable)` → `Page<CompraResponse>`.
- Se eliminó el filtrado manual de `deletedAt` en `getAll()` (ya lo maneja el `@Query`).

#### CompraApi — Interfaz actualizada
- **Archivo**: `modules/compras/api/CompraApi.java`
- `getAll()`, `getByUsuario()`, `getByFecha()` ahora reciben `Pageable` y retornan `Page<CompraResponse>`.

#### CompraController — Parámetros de paginación
- **Archivo**: `modules/compras/controller/CompraController.java`
- `GET /api/compras/v1` recibe `page` (default 0) y `size` (default 20).
- `GET /api/compras/v1/usuario/{idUsuario}` recibe `page` y `size`.
- `GET /api/compras/v1/fecha` recibe `page` y `size`.

#### CompraRepository — Query unificado con filtros
- **Archivo**: `modules/compras/repository/CompraRepository.java`
- **Por qué**: el historial de compras necesitaba filtrar por proveedor, tipo de comprobante y rango de fechas. En vez de crear 3 endpoints separados, se unificaron todos los filtros en un solo `@Query` parametrizado con condiciones null-safe.
- **Qué cambió**:
  - Nuevo método `findAllFiltered()` con query que usa `:param IS NULL OR campo = :param` para cada filtro.
  - Los métodos anteriores (`findAllPaginated`, `findByCreatedAtBetween...`, `findByIdUsuario...`) se comentaron (NO se borraron).

#### CompraService — getAllFiltered
- **Archivo**: `modules/compras/service/CompraService.java`
- Nuevo método `getAllFiltered()` que delega al repositorio unificado. Los métodos viejos se comentaron.

#### CompraApi — Interfaz actualizada
- **Archivo**: `modules/compras/api/CompraApi.java`
- `getAllFiltered()` reemplaza los 3 métodos anteriores. Los viejos se comentaron.

#### CompraController — Filtros en GET /v1
- **Archivo**: `modules/compras/controller/CompraController.java`
- `GET /api/compras/v1` ahora recibe los filtros opcionales: `proveedor`, `tipoComprobante`, `desde`, `hasta`, `sortTotal` (asc/desc), además de `page` y `size`. Los endpoints `GET /v1/fecha` y `GET /v1/usuario/{idUsuario}` se comentaron.

#### CompraService — Revertir stock al eliminar compra
- **Archivo**: `modules/compras/service/CompraService.java`
- **Por qué**: al eliminar una compra, el stock que se incrementó al crearla no se revertía. Esto dejaba el inventario desactualizado.
- **Qué cambió**: `delete(UUID id, UUID idUsuario)` ahora revierte el stock antes del soft delete, recorriendo cada detalle de la compra:
  - **Maneja lotes**: busca el `Lote` y `MovimientoStock` asociados y les hace soft delete.
  - **No maneja lotes**: llama a `inventarioApi.disminuir()` para bajar el stock.
  - Si el producto ya no existe, imprime un WARNING y continúa con el delete sin lanzar error.
  - Se agregó `idUsuario` como parámetro para registrar el movimiento de reversión.

#### CompraApi — Interfaz delete actualizada
- **Archivo**: `modules/compras/api/CompraApi.java`
- `delete(UUID id)` → `delete(UUID id, UUID idUsuario)`.

#### CompraController — idUsuario en DELETE
- **Archivo**: `modules/compras/controller/CompraController.java`
- `DELETE /v1/{id}` ahora recibe `idUsuario` en el header para pasarlo al servicio y registrar el movimiento de reversión.
