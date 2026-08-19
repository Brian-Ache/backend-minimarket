-- =====================================================================
-- Migración — Fase 4 del plan de corrección de bugs
-- =====================================================================
-- Aplica sobre una base que ya tenga la migración de la Fase 3.
-- Si la base se crea desde cero, 00_init.sql ya incluye todo esto.
--
-- Uso:  mysql -u root -p < 03_migracion_fase4.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- B-11 · El corte guarda su propio desglose
-- Hasta ahora el detalle del arqueo no se persistía y el historial devolvía
-- todo en cero. Un corte es un documento contable: se congela al cerrarlo,
-- no se recalcula al consultarlo.
-- ---------------------------------------------------------------------
ALTER TABLE sesiones_caja
    ADD COLUMN total_ventas            FLOAT NULL AFTER diferencia,
    ADD COLUMN cantidad_ventas         INT   NULL AFTER total_ventas,
    ADD COLUMN total_compras           FLOAT NULL AFTER cantidad_ventas,
    ADD COLUMN cantidad_compras        INT   NULL AFTER total_compras,
    ADD COLUMN total_entradas_manuales FLOAT NULL AFTER cantidad_compras,
    ADD COLUMN total_salidas_manuales  FLOAT NULL AFTER total_entradas_manuales;

-- Los cortes ya cerrados quedan con el desglose en NULL: no hay forma de
-- reconstruirlo con fidelidad, y es preferible que la API devuelva null
-- (dato desconocido) antes que un cero que parece un valor real.

-- ---------------------------------------------------------------------
-- B-13 · Ganancia real
-- Congela el costo del producto en cada línea de venta.
-- ---------------------------------------------------------------------
ALTER TABLE detalles_ventas
    ADD COLUMN costo_unitario FLOAT NULL AFTER precio_unitario;

-- Backfill opcional para las ventas viejas, usando el costo actual del
-- producto. Es una aproximación: solo correlo si preferís una ganancia
-- estimada del histórico antes que campos vacíos.
--
--   UPDATE detalles_ventas d
--     JOIN productos p ON p.id = d.id_producto
--      SET d.costo_unitario = p.costo
--    WHERE d.costo_unitario IS NULL AND p.costo IS NOT NULL;

-- ---------------------------------------------------------------------
-- Índice para los reportes de dinero, que filtran por fecha de cobro
-- ---------------------------------------------------------------------
ALTER TABLE ventas
    ADD KEY ix_ventas_fecha_cobro (fecha_cobro, deleted_at);

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema='minimarket' AND table_name='sesiones_caja'
        AND column_name LIKE 'total\_%')                   AS cols_corte,
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema='minimarket' AND table_name='detalles_ventas'
        AND column_name='costo_unitario')                  AS col_costo,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema='minimarket' AND index_name='ix_ventas_fecha_cobro') AS ix_fecha_cobro;
