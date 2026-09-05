# Para implementar — cerrado en la 0.5.0

> Los cuatro trabajos que listaba este documento están implementados. Queda como registro de qué
> se decidió y por qué; el detalle de **qué** cambió está en el `CHANGELOG` de la 0.5.0 y en
> [`api-endpoints.md`](api-endpoints.md).

## Lo que se hizo

| Trabajo | Resultado |
|---|---|
| **Stock inicial en el alta** | `POST /api/productos/v1` acepta `cantidadInicial` y `loteInicial`, todo en una transacción. Se eliminó `POST /api/inventario/v1/stock`, que era inalcanzable |
| **Ajuste manual por lote** | `POST /api/inventario/v1/lotes/ajustar`, parcial, más `GET /lotes/ajustables/{idProducto}` |
| **Precio de referencia por proveedor** | Tabla `producto_proveedor` y tres endpoints en productos, más la vista combinada en compras |
| **Paginación** | Los seis listados que faltaban: lotes (×5), cortes, reporte de inventario, categorías, proveedores y usuarios |

Migraciones `09`, `10` y `11`. Tests: 258 en 37 archivos.

## Decisiones que conviene no volver a discutir

- **El descuento por lotes ya era FEFO**, no FIFO: la consulta siempre ordenó por fecha de
  vencimiento. Lo único que cambió fue el nombre (`findParaDescuentoFefo`) y los comentarios.
- **`findParaDescuentoFefo` es la única puerta para bloquear los lotes de un producto**, y su
  orden es parte del contrato. El ajuste por lote toma todos los lotes por ahí y resuelve los
  conteos en memoria; bloquearlos uno por uno en el orden en que los manda el cliente abriría un
  ciclo con las ventas y las anulaciones.
- **El catálogo de precios vive en `productos`, no en `proveedores`.** `productos -> proveedores`
  ya existe, así que colgarlo del otro lado cerraría un ciclo entre los dos módulos. La vista que
  cruza el precio de referencia con lo que realmente se pagó vive en `compras`, el único módulo
  que ya depende de los dos.
- **`productos.id_proveedor` se conserva** como proveedor habitual: "a quién le compro siempre" y
  "qué precios conozco" son dos datos distintos.
- **La carga inicial se registra como `AJUSTE`** con motivo "Carga inicial de stock", no con un
  valor propio del enum: `movimientos_stock.tipo` es un `ENUM` de MySQL y sumarle un valor obliga
  a migrar la tabla y a revisar todo lo que filtra por tipo.
- **Baja de proveedor conserva sus precios; baja de producto se lleva los suyos.** La primera es
  reversible y perder el catálogo en cada una sería destructivo; en la segunda, las filas
  huérfanas seguían ocupando el índice único.
- **Un producto con lotes no lleva fila de `stock`.** Esa fila no la lee nadie.

## Lo que quedó pendiente

- **Los importes siguen siendo `float`.** `producto_proveedor.precio_referencia` nació en
  `DECIMAL` porque es una columna nueva que no se suma con ninguna otra, pero el resto del
  sistema no se tocó. Migrarlo entero es un trabajo aparte: toca todas las entidades, todos los
  DTOs y el histórico.
- **`lote.estado` se escribe al crear el lote y no se refresca nunca.** La API devuelve el estado
  calculado al leer, así que la columna es dato muerto. Sacarla es una migración de una línea,
  pero conviene hacerla junto con otra.
- **No hay tests de integración contra una base real.** Las consultas JPQL nuevas —la de lotes
  ajustables y la de última compra por proveedor— se verificaron a mano. `contextLoads` es el
  único test que necesita MySQL levantado.
- **Falta Flyway.** Con once migraciones aplicadas a mano ya duele.
