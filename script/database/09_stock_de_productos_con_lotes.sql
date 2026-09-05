-- =====================================================================
-- Un producto con lotes no lleva fila de stock
-- =====================================================================
-- Las existencias de un producto tienen una sola fuente segun el
-- producto: la tabla stock para los comunes y la suma de sus lotes
-- activos para los que manejan lotes. getExistenciasPorProducto y el
-- reporte de inventario ya trabajan asi.
--
-- El alta de producto, sin embargo, insertaba siempre una fila de stock
-- en cero, tambien para los que manejan lotes. Esa fila no la lee nadie:
-- no representa existencias, no se actualiza cuando entra o sale
-- mercaderia por lote, y ocupa el indice unico uk_stock_producto_activo
-- sin motivo. Peor todavia, es la que hacia que
-- POST /api/inventario/v1/stock fuera inalcanzable —siempre respondia
-- "El producto ya tiene stock inicializado"—, endpoint que en esta
-- version se elimina porque el alta de producto ya carga las
-- existencias iniciales.
--
-- Este script da de baja esas filas huerfanas. Es baja logica, como todo
-- el resto del sistema, asi que es reversible: para revertirla alcanza
-- con poner deleted_at en NULL en las filas que este script marco.
--
-- Solo toca filas en CERO. Una fila con unidades de un producto que hoy
-- maneja lotes es un dato real que se perderia de vista, y ademas
-- significa que en algun momento se cambio manejaLotes con existencias
-- cargadas; eso es justamente lo que la aplicacion pasa a rechazar. Esas
-- filas se listan abajo para revisarlas a mano.
--
-- ProductoService.delete tolera que el producto no tenga fila de stock
-- (.orElse(null)), asi que la baja no rompe ningun flujo.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 09_stock_de_productos_con_lotes.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Revisar primero: filas de stock CON unidades de productos que manejan
-- lotes. Este script no las toca. Son existencias reales que la
-- aplicacion no muestra —para ese producto lee los lotes—, asi que hay
-- que decidir producto por producto si se cargan como lote o si el
-- producto en realidad no deberia manejar lotes.
-- ---------------------------------------------------------------------
SELECT BIN_TO_UUID(p.id, 0) AS producto,
       p.nombre,
       s.cantidad           AS unidades_en_stock
  FROM stock s
  JOIN productos p ON p.id = s.id_producto
 WHERE s.deleted_at IS NULL
   AND p.maneja_lotes = b'1'
   AND s.cantidad > 0;

-- ---------------------------------------------------------------------
-- Cuantas filas va a dar de baja el UPDATE de abajo
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS filas_a_dar_de_baja
  FROM stock s
  JOIN productos p ON p.id = s.id_producto
 WHERE s.deleted_at IS NULL
   AND p.maneja_lotes = b'1'
   AND s.cantidad = 0;

-- ---------------------------------------------------------------------
-- Baja logica de las filas en cero
-- ---------------------------------------------------------------------
UPDATE stock s
  JOIN productos p ON p.id = s.id_producto
   SET s.deleted_at = NOW(6)
 WHERE s.deleted_at IS NULL
   AND p.maneja_lotes = b'1'
   AND s.cantidad = 0;

-- ---------------------------------------------------------------------
-- Verificacion: tiene que dar 0 filas activas en cero. Si quedan filas,
-- son las que tienen unidades y salieron en la primera consulta.
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS quedan_en_cero
  FROM stock s
  JOIN productos p ON p.id = s.id_producto
 WHERE s.deleted_at IS NULL
   AND p.maneja_lotes = b'1'
   AND s.cantidad = 0;
