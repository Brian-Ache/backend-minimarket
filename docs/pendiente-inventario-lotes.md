## Problema: integración de productos con manejaLotes en el módulo inventario

### Contexto

El módulo `inventario` unificó stock y lotes, pero **no diferencia** si un producto maneja lotes o no. Esto genera inconsistencias.

### Problemas actuales

| Operación | Producto sin lotes | Producto con lotes |
|---|---|---|
| `aumentar(MovimientoStockRequest)` | ✅ Funciona | ❌ Debería crear un `Lote`, no modificar `Stock` |
| `disminuir(MovimientoStockRequest)` | ✅ Funciona | ❌ Debería descontar del lote más próximo a vencer (FIFO) |
| `controlarStock(AjusteStockRequest)` | ✅ Ajusta a valor exacto | ❌ No tiene sentido sobre el total global |
| `crearLote(LoteRequest)` | ✅ Rechazar (no aplica) | ❌ No registra `MovimientoStock` con `idLote` |
| `obtenerStockDisponible(UUID)` | ❌ No existe | ❌ No existe |

### Casos no cubiertos

- **Descuento FIFO por lote**: al vender un producto con lotes, debería descontar primero del lote más próximo a vencer
- **Stock disponible unificado**: no hay un método que devuelva `int` stock total según el tipo de producto
- **Validación en aumentar/disminuir**: deberían consultar `productosApi.getById().isManejaLotes()` y rechazar si `true` (el caller debe usar `crearLote`)

### Posible solución

```
En InventarioApi:

  // Solo para productos con manejaLotes = false
  aumentar(MovimientoStockRequest)
  disminuir(MovimientoStockRequest)
  controlarStock(UUID, AjusteStockRequest)

  // Solo para productos con manejaLotes = true
  crearLote(LoteRequest)           // ya existe, falta registrar MovimientoStock
  descontarDeLote(UUID lote, int cantidad, UUID usuario)  // nuevo

  // Unificado (funciona para ambos)
  obtenerStockDisponible(UUID idProducto)  // nuevo, retorna int

Internamente:
  aumentar/disminuir/controlarStock validan isManejaLotes() y lanzan BadRequestException si true
  crearLote registra MovimientoStock con idLote
  obtenerStockDisponible suma Stock.cantidad o lotes vigentes según el caso
```
