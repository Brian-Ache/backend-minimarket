# Changelog

## 0.1.0 (2026-07-06)

### Funcionalidades
- CRUD de productos con búsqueda por código de barras y soft delete
- Registro de ventas con soporte de productos del sistema y productos manuales
- Descuento automático de stock al realizar ventas
- Registro de compras/ingresos con aumento automático de stock
- Gestión de lotes con seguimiento de vencimientos (vigentes, próximos a vencer, vencidos)
- Ajuste de stock por conteo físico
- Trazabilidad completa via movimientos de stock (COMPRA, VENTA, AJUSTE, MERMA)
- Roles de usuario: ADMIN y EMPLEADO

### Técnico
- Spring Boot 3.5.13 + Java 21 + MySQL
- API REST documentada con SpringDoc OpenAPI 2.5.0
- Soft delete en Producto, Usuario y Lote
- Mapeo polimórfico de detalles de venta (producto sistema / manual)
- Arquitectura en capas: Controller -> Service -> Repository
