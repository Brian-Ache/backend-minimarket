-- =====================================================================
-- Un proveedor no repite numero dentro del mismo tipo de comprobante
-- =====================================================================
-- nro_comprobante era texto libre sin ningun control, ni en la aplicacion
-- ni en la base: cargar dos veces el mismo remito duplicaba el ingreso de
-- stock y la salida de caja, y no quedaba ninguna senal de que habia
-- pasado.
--
-- La unicidad es por proveedor y por tipo:
--   * dos proveedores distintos pueden emitir el mismo numero, y son
--     comprobantes distintos;
--   * un mismo proveedor numera por separado sus facturas y sus remitos,
--     asi que su factura 0001-00001234 y su remito 0001-00001234 son dos
--     documentos y tienen que poder convivir.
--
-- Quedan fuera del indice, a proposito, las compras sin numero (no hay
-- con que compararlas) y las anuladas (anular libera el numero para
-- volver a cargar la compra bien).
--
-- Todo eso sale de una sola columna generada y de como MySQL trata los
-- NULL:
--   * CONCAT devuelve NULL si algun argumento es NULL, asi que una compra
--     sin numero queda exenta sola;
--   * el IFNULL sobre el tipo lo lleva a cadena vacia, para que un tipo
--     sin cargar siga contando como un valor y no abra un agujero;
--   * el IF sobre deleted_at deja fuera a las anuladas;
--   * y los NULL no chocan entre si en un indice unico, que es lo que
--     hace efectivas las tres exenciones.
-- Mismo patron que uk_productos_barcode_activo y
-- uk_categorias_nombre_activo.
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
SELECT BIN_TO_UUID(c.id_proveedor, 0)      AS proveedor,
       IFNULL(c.tipo_comprobante, '')      AS tipo,
       c.nro_comprobante,
       COUNT(*)                            AS cantidad,
       GROUP_CONCAT(BIN_TO_UUID(c.id, 0))  AS compras
  FROM compras c
 WHERE c.deleted_at IS NULL
   AND c.id_proveedor IS NOT NULL
   AND c.nro_comprobante IS NOT NULL
 GROUP BY c.id_proveedor, IFNULL(c.tipo_comprobante, ''), c.nro_comprobante
HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------
-- Indice unico sobre las compras activas
-- ---------------------------------------------------------------------
ALTER TABLE compras
    ADD COLUMN comprobante_activo VARCHAR(72)
        GENERATED ALWAYS AS (
            IF(deleted_at IS NULL,
               CONCAT(IFNULL(tipo_comprobante, ''), '|', nro_comprobante),
               NULL)
        ) VIRTUAL,
    ADD UNIQUE KEY uk_compras_proveedor_comprobante (id_proveedor, comprobante_activo);

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'compras'
        AND column_name = 'comprobante_activo') AS col_generada,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_compras_proveedor_comprobante') AS uk_comprobante;
