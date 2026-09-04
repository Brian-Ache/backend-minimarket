-- =====================================================================
-- Un proveedor no puede tener dos compras con el mismo comprobante
-- =====================================================================
-- nro_comprobante era texto libre sin ningun control, ni en la aplicacion
-- ni en la base: cargar dos veces el mismo remito duplicaba el ingreso de
-- stock y la salida de caja, y no quedaba ninguna senal de que habia
-- pasado.
--
-- La unicidad es por proveedor y no global: dos proveedores distintos
-- pueden emitir el mismo numero y son comprobantes distintos. Quedan
-- fuera del indice, a proposito, las compras sin proveedor y las que no
-- traen numero: no hay con que compararlas.
--
-- La columna generada saca de la unicidad a las compras anuladas, con el
-- mismo patron que uk_productos_barcode_activo y uk_categorias_nombre_activo:
-- si una compra se anula, su numero vuelve a estar disponible para volver
-- a cargarla bien. En MySQL los NULL no chocan entre si en un indice
-- unico, asi que las tres exenciones salen del mismo mecanismo.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 07_unicidad_comprobante_por_proveedor.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Duplicados existentes (correr primero: si devuelve filas, el ALTER de
-- abajo falla). Son compras cargadas dos veces: hay que decidir cual
-- queda y anular la otra con
--   UPDATE compras SET deleted_at = NOW(6) WHERE id = ...
-- teniendo en cuenta que el stock que ingreso esa compra sigue cargado.
-- ---------------------------------------------------------------------
SELECT BIN_TO_UUID(c.id_proveedor, 0) AS proveedor,
       c.nro_comprobante,
       COUNT(*)                       AS cantidad,
       GROUP_CONCAT(BIN_TO_UUID(c.id, 0)) AS compras
  FROM compras c
 WHERE c.deleted_at IS NULL
   AND c.id_proveedor IS NOT NULL
   AND c.nro_comprobante IS NOT NULL
 GROUP BY c.id_proveedor, c.nro_comprobante
HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------
-- Indice unico sobre las compras activas
-- ---------------------------------------------------------------------
ALTER TABLE compras
    ADD COLUMN nro_comprobante_activo VARCHAR(50)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, nro_comprobante, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_compras_proveedor_comprobante (id_proveedor, nro_comprobante_activo);

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'compras'
        AND column_name = 'nro_comprobante_activo') AS col_generada,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_compras_proveedor_comprobante') AS uk_comprobante;
