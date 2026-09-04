-- =====================================================================
-- Unicidad de barcode entre productos activos
-- =====================================================================
-- El chequeo "ya existe un producto con ese barcode" vivia solo en el
-- servicio, entre un SELECT y un INSERT: dos altas simultaneas con el
-- mismo codigo pasaban las dos. Con dos filas activas, la consulta por
-- barcode devuelve mas de un resultado y Spring Data corta con
-- IncorrectResultSizeDataAccessException, asi que a partir de ahi tanto
-- GET /api/productos/v1/barcode/{barcode} como cualquier alta con ese
-- codigo respondian 500 de forma permanente.
--
-- Mismo patron que uk_stock_producto_activo: una columna generada que
-- vale el barcode mientras la fila esta activa y NULL cuando esta
-- borrada. MySQL considera distintos los NULL en un indice unico, asi
-- que los productos dados de baja no ocupan el codigo, y un barcode
-- NULL en una fila activa tampoco choca con otro.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 05_unicidad_barcode.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Duplicados existentes (correr primero: si devuelve filas, el ALTER de
-- abajo falla). Consolidar a mano cual queda activo y dar de baja el
-- resto con UPDATE productos SET deleted_at = NOW(6) WHERE id = ...
-- ---------------------------------------------------------------------
SELECT barcode, COUNT(*) AS cantidad
  FROM productos
 WHERE deleted_at IS NULL
   AND barcode IS NOT NULL
 GROUP BY barcode
HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------
-- Indice unico sobre los productos activos
-- ---------------------------------------------------------------------
ALTER TABLE productos
    ADD COLUMN barcode_activo VARCHAR(255)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, barcode, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_productos_barcode_activo (barcode_activo);

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'productos'
        AND column_name = 'barcode_activo') AS col_generada,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_productos_barcode_activo') AS uk_barcode;
