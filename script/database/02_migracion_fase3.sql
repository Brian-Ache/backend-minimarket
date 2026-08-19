-- =====================================================================
-- Migración — Fase 3 del plan de corrección de bugs
-- =====================================================================
-- Aplica sobre una base creada con la versión anterior de 00_init.sql.
-- Si la base se crea desde cero, 00_init.sql ya incluye todo esto y este
-- script no hace falta.
--
-- Uso:  mysql -u root -p < 02_migracion_fase3.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- B-05 · Reversa de anulaciones
-- Vincula cada movimiento de stock con la venta o compra que lo originó.
-- Sin esto no se puede saber de qué lote salió cada unidad cuando el FIFO
-- repartió una línea entre varios lotes.
-- ---------------------------------------------------------------------
ALTER TABLE movimientos_stock
    ADD COLUMN id_referencia BINARY(16) NULL AFTER id_usuario,
    ADD KEY ix_mov_stock_referencia (id_referencia, tipo, deleted_at);

-- ---------------------------------------------------------------------
-- B-06 · Reversas en caja
-- Nuevo origen para la devolución de plata al anular una compra.
-- ---------------------------------------------------------------------
ALTER TABLE movimientos_caja
    DROP CHECK ck_mov_caja_origen;

ALTER TABLE movimientos_caja
    ADD CONSTRAINT ck_mov_caja_origen
        CHECK (origen IS NULL OR origen IN ('MANUAL','VENTA','COMPRA','REVERSA'));

-- ---------------------------------------------------------------------
-- B-15 · Una sola fila de stock activa por producto
-- Verificar primero que no haya duplicados; si los hay, consolidarlos a
-- mano antes de crear el índice:
--
--   SELECT BIN_TO_UUID(id_producto,0), COUNT(*)
--     FROM stock WHERE deleted_at IS NULL
--    GROUP BY id_producto HAVING COUNT(*) > 1;
-- ---------------------------------------------------------------------
ALTER TABLE stock
    ADD COLUMN producto_activo BINARY(16)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, id_producto, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_stock_producto_activo (producto_activo);

-- ---------------------------------------------------------------------
-- B-18 · Una sola sesión de caja abierta
-- Verificar primero:
--
--   SELECT COUNT(*) FROM sesiones_caja
--    WHERE estado = 'ABIERTA' AND deleted_at IS NULL;
-- ---------------------------------------------------------------------
ALTER TABLE sesiones_caja
    ADD COLUMN sesion_abierta VARCHAR(10)
        GENERATED ALWAYS AS (IF(estado = 'ABIERTA' AND deleted_at IS NULL, 'ABIERTA', NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_sesiones_una_abierta (sesion_abierta);

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'movimientos_stock'
        AND column_name = 'id_referencia')            AS mov_stock_id_referencia,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket' AND index_name = 'uk_stock_producto_activo') AS uk_stock,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket' AND index_name = 'uk_sesiones_una_abierta')  AS uk_sesion;
