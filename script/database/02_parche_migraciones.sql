-- =====================================================================
-- Parche acumulado del esquema hasta la version 0.3.0
-- =====================================================================
-- Reemplaza las migraciones individuales de las fases 3, 4 y 5, estado de
-- usuario, SUPERADMIN e invitaciones.
--
-- Aplicar una sola vez sobre una base existente anterior a este esquema,
-- con la aplicacion detenida. Para una base nueva usar 00_init_limpio.sql.
--
-- Uso: mysql -u root -p < 02_parche_migraciones.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Fase 3: reversas de stock y restricciones de concurrencia
-- ---------------------------------------------------------------------
ALTER TABLE movimientos_stock
    ADD COLUMN id_referencia BINARY(16) NULL AFTER id_usuario,
    ADD KEY ix_mov_stock_referencia (id_referencia, tipo, deleted_at);

ALTER TABLE movimientos_caja
    DROP CHECK ck_mov_caja_origen;

ALTER TABLE movimientos_caja
    ADD CONSTRAINT ck_mov_caja_origen
        CHECK (origen IS NULL OR origen IN ('MANUAL','VENTA','COMPRA','REVERSA'));

-- Verificar antes que no haya duplicados. Si los hay, consolidarlos a mano.
-- SELECT BIN_TO_UUID(id_producto,0), COUNT(*)
--   FROM stock WHERE deleted_at IS NULL
--  GROUP BY id_producto HAVING COUNT(*) > 1;
ALTER TABLE stock
    ADD COLUMN producto_activo BINARY(16)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, id_producto, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_stock_producto_activo (producto_activo);

-- Verificar antes que no haya mas de una sesion abierta.
-- SELECT COUNT(*) FROM sesiones_caja
--  WHERE estado = 'ABIERTA' AND deleted_at IS NULL;
ALTER TABLE sesiones_caja
    ADD COLUMN sesion_abierta VARCHAR(10)
        GENERATED ALWAYS AS (IF(estado = 'ABIERTA' AND deleted_at IS NULL, 'ABIERTA', NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_sesiones_una_abierta (sesion_abierta);

-- ---------------------------------------------------------------------
-- Fase 4: desglose del corte, costo historico e indice de cobro
-- ---------------------------------------------------------------------
ALTER TABLE sesiones_caja
    ADD COLUMN total_ventas            FLOAT NULL AFTER diferencia,
    ADD COLUMN cantidad_ventas         INT   NULL AFTER total_ventas,
    ADD COLUMN total_compras           FLOAT NULL AFTER cantidad_ventas,
    ADD COLUMN cantidad_compras        INT   NULL AFTER total_compras,
    ADD COLUMN total_entradas_manuales FLOAT NULL AFTER cantidad_compras,
    ADD COLUMN total_salidas_manuales  FLOAT NULL AFTER total_entradas_manuales;

ALTER TABLE detalles_ventas
    ADD COLUMN costo_unitario FLOAT NULL AFTER precio_unitario;

-- Backfill opcional para ventas historicas, usando el costo actual.
-- UPDATE detalles_ventas d
--   JOIN productos p ON p.id = d.id_producto
--    SET d.costo_unitario = p.costo
--  WHERE d.costo_unitario IS NULL AND p.costo IS NOT NULL;
ALTER TABLE ventas
    ADD KEY ix_ventas_fecha_cobro (fecha_cobro, deleted_at);

-- ---------------------------------------------------------------------
-- Fase 5: nombre plural de la tabla de refresh tokens
-- ---------------------------------------------------------------------
-- Detener la aplicacion antes de renombrar: las sesiones abiertas se
-- invalidan y sera necesario volver a iniciar sesion.
RENAME TABLE refresh_token TO refresh_tokens;

ALTER TABLE refresh_tokens
    RENAME INDEX uk_refresh_token_token_hash TO uk_refresh_tokens_token_hash,
    RENAME INDEX ix_refresh_token_user_activo TO ix_refresh_tokens_user_activo;

ALTER TABLE refresh_tokens DROP FOREIGN KEY fk_refresh_token_usuario;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------
-- Estado de usuario y unicidad de username
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    ADD COLUMN estado ENUM('PENDIENTE','ACTIVO','BLOQUEADO') NOT NULL DEFAULT 'PENDIENTE'
        AFTER rol;

UPDATE usuarios SET estado = IF(enabled = 1, 'ACTIVO', 'PENDIENTE');
ALTER TABLE usuarios DROP COLUMN enabled;

-- Verificar antes que no haya usernames repetidos. Si los hay, resolverlos
-- y ejecutar nuevamente solo este ALTER.
-- SELECT username, COUNT(*) FROM usuarios
--  WHERE deleted_at IS NULL
--  GROUP BY username HAVING COUNT(*) > 1;
ALTER TABLE usuarios
    ADD UNIQUE KEY uk_usuarios_username (username);

-- ---------------------------------------------------------------------
-- Rol SUPERADMIN
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    MODIFY COLUMN rol ENUM('SUPERADMIN','ADMIN','EMPLEADO') NOT NULL;

SET @id_superadmin = UUID_TO_BIN('11111111-1111-4111-8111-111111111100', 0);
INSERT INTO usuarios (id, nombre, apellido, username, email, hash_password, rol, estado, created_at, updated_at)
VALUES (
    @id_superadmin, 'Super', 'Admin', 'superadmin', 'superadmin@minimarket.local',
    '$2a$10$th8pTK4hJAP0kr9Dzh/Jh.RyVhLMd4SkmREGeImqB3F/lYOuEw6ky',
    'SUPERADMIN', 'ACTIVO', NOW(6), NOW(6)
)
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- Alternativa: promover una cuenta existente en vez del alta de arriba.
-- UPDATE usuarios SET rol = 'SUPERADMIN', updated_at = NOW(6)
--  WHERE email = 'tu-cuenta@dominio.com' AND deleted_at IS NULL;
-- Cambiar la contrasena inicial apenas se ingresa.

-- ---------------------------------------------------------------------
-- Alta por invitacion
-- ---------------------------------------------------------------------
ALTER TABLE auth_tokens
    MODIFY COLUMN token_type ENUM('PASSWORD_RESET','VERIFICATION','INVITATION') NOT NULL;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'usuarios'
        AND column_name = 'estado') AS col_estado,
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'usuarios'
        AND column_name = 'enabled') AS col_enabled_vieja,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket' AND index_name = 'uk_usuarios_username') AS uk_username,
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'auth_tokens'
        AND column_name = 'token_type') AS token_type;
