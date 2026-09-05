-- =====================================================================
-- El nombre de una categoria dada de baja vuelve a estar disponible
-- =====================================================================
-- uk_categorias_nombre abarcaba tambien las filas borradas, asi que una
-- categoria dada de baja se quedaba con su nombre para siempre. El
-- servicio validaba los duplicados filtrando por deleted_at, con lo cual
-- daba el nombre por libre y el INSERT chocaba contra el indice: el alta
-- respondia 409 "La operacion choca con una restriccion de datos
-- existente", sin forma de saber que el nombre lo retenia algo invisible
-- y sin forma de recuperarlo. Lo mismo al renombrar una categoria.
--
-- Se reemplaza por un indice unico sobre las categorias activas, con el
-- mismo patron de columna generada que uk_stock_producto_activo y
-- uk_productos_barcode_activo: MySQL considera distintos los NULL en un
-- indice unico, asi que las filas borradas dejan de reservar el nombre y
-- la base pasa a decir lo mismo que el servicio.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 06_nombre_categoria_reutilizable.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Nombres retenidos hoy por categorias dadas de baja (informativo: son
-- los que quedan liberados al aplicar esto)
-- ---------------------------------------------------------------------
SELECT c.nombre AS nombre_liberado
  FROM categorias c
 WHERE c.deleted_at IS NOT NULL;

-- ---------------------------------------------------------------------
-- Indice unico solo entre las categorias activas
-- ---------------------------------------------------------------------
-- El indice viejo era ademas el unico sobre nombre, y la busqueda por
-- nombre del servicio no puede usar la columna generada, asi que se deja
-- una KEY comun en su lugar.
ALTER TABLE categorias
    DROP INDEX uk_categorias_nombre,
    ADD COLUMN nombre_activo VARCHAR(100)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, nombre, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_categorias_nombre_activo (nombre_activo),
    ADD KEY ix_categorias_nombre (nombre);

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_categorias_nombre') AS uk_vieja_debe_ser_0,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_categorias_nombre_activo') AS uk_nueva,
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'categorias'
        AND column_name = 'nombre_activo') AS col_generada;
