# Arquitectura — backend-minimarket v0.1.0

## Stack Tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| Java | 21 | Lenguaje |
| Spring Boot | 3.5.13 | Framework principal |
| Spring Data JPA / Hibernate | -- | ORM y persistencia |
| Spring Security | -- | Seguridad (en configuración inicial) |
| SpringDoc OpenAPI | 2.5.0 | Documentación de API |
| MySQL | 8.x | Base de datos |
| Lombok | -- | Reducción de boilerplate |
| Maven | 3.9.14 | Build y dependencias |

## Estructura del Proyecto

```
src/main/java/com/SolucionesInformaticasBA/minimarket/
+-- MinimarketApplication.java          # Entry point
+-- config/                             # Configuraciones (Security, CORS)
+-- controller/                         # Controladores REST
+-- dto/
|   +-- request/                        # DTOs de entrada
|   +-- response/                       # DTOs de salida
+-- mapper/                             # Conversión Entity <-> DTO
+-- model/
|   +-- entity/                         # Entidades JPA
|   +-- enums/                          # Enumeraciones (Rol, TipoMovimiento)
+-- repository/                         # Repositorios JPA
+-- service/                            # Lógica de negocio
```

## Arquitectura en Capas

```
[Controller] -> [Service] -> [Repository] -> [Database]
     |
 [DTO/Mapper]
```

- **Controller:** Endpoints REST, recibe/valida requests, delega en Service.
- **Service:** Lógica de negocio, orquesta operaciones, coordina repositorios.
- **Repository:** Acceso a datos via Spring Data JPA.
- **Mapper:** Convierte entre entidades JPA y DTOs.

## Modelo de Datos

### Tablas

| Tabla | Propósito |
|---|---|
| `producto` | Catálogo de productos (con stock, barcode, soft delete) |
| `usuario` | Usuarios del sistema (ADMIN / EMPLEADO) |
| `venta` | Cabecera de venta |
| `detalle_venta` | Líneas de venta (producto sistema o manual) |
| `compra` | Cabecera de compra/ingreso |
| `detalle_compra` | Líneas de compra |
| `lote` | Lotes por producto con fecha de vencimiento |
| `movimiento_stock` | Trazabilidad de movimientos de stock |

### Principales Relaciones

```
Usuario --+-- Producto (creado_por)
          +-- Venta (usuario_id)
          +-- Compra (usuario_id)
          +-- MovimientoStock (usuario_id)
          +-- Lote (creado_por)

Producto --+-- DetalleVenta (id_producto, nullable)
           +-- DetalleCompra (id_producto)
           +-- Lote (id_producto)
           +-- MovimientoStock (id_producto)

Venta ---- DetalleVenta
Compra --- DetalleCompra
```

## API REST

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/productos` | Crear producto |
| GET | `/api/productos` | Listar productos |
| GET | `/api/productos/{id}` | Obtener producto |
| GET | `/api/productos/barcode/{barcode}` | Buscar por código de barras |
| PUT | `/api/productos/{id}` | Actualizar producto |
| DELETE | `/api/productos/{id}` | Eliminar (soft delete) |
| POST | `/api/ventas` | Realizar venta |
| GET | `/api/ventas` | Listar ventas |
| GET | `/api/ventas/{id}` | Obtener venta |
| POST | `/api/compras` | Registrar compra |
| GET | `/api/compras` | Listar compras |
| GET | `/api/compras/{id}` | Obtener compra |
| POST | `/api/lotes` | Crear lote |
| GET | `/api/lotes` | Listar lotes |
| GET | `/api/lotes/vencimiento/por-vencer` | Lotes próximos a vencer |
| GET | `/api/lotes/vencimientos/vencidos` | Lotes vencidos |
| GET | `/api/lotes/vencimientos/vigentes` | Lotes vigentes |
| POST | `/api/stock/ajuste` | Ajustar stock por conteo |

## Seguridad (Estado Actual)

- Spring Security configurado pero **completamente abierto**
- CSRF deshabilitado
- CORS habilitado para `localhost:5173`
- Identificación de usuario via header HTTP `usuarioId`
- Contraseñas almacenadas sin hashear

## Patrones Utilizados

- **DTO Pattern:** Objetos de transferencia separados de entidades
- **Mapper Pattern:** Conversión explícita Entity <-> DTO
- **Repository Pattern:** Spring Data JPA
- **Polimorfismo Jackson:** DetalleVenta con subtipos (Producto/Manual)
- **Soft Delete:** Eliminación lógica con `fecha_eliminacion`
- **Entity Lifecycle:** `@PrePersist` / `@PreUpdate` para auditoría automática
- **Inyección por Constructor:** Via Lombok `@RequiredArgsConstructor`
