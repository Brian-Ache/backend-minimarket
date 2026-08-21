-- =====================================================================
-- Migración — Estado de usuario y unicidad de username
-- =====================================================================
-- Aplica sobre una base que ya tenga la migración de la Fase 5.
-- Si la base se crea desde cero, 00_init.sql ya incluye todo esto.
--
-- Uso:  mysql -u root -p < 05_migracion_estado_usuario.sql
-- =====================================================================

USE minimarket;

-- Los dos pasos son independientes y se aplican en orden. Si el paso 2 falla por usernames
-- repetidos, el paso 1 ya quedó aplicado: no hace falta revertir nada, se corrigen los
-- duplicados y se vuelve a correr **solo** el ALTER del paso 2.

-- ---------------------------------------------------------------------
-- 1. `enabled` pasa a `estado`
--
-- El flag booleano solo distinguía "verificado" de "no verificado" y no
-- dejaba lugar para el bloqueo: suspender a alguien obligaba a borrarlo,
-- perdiendo la diferencia entre "se fue" y "no puede entrar por ahora".
--
-- Equivalencia: enabled = 1 -> ACTIVO · enabled = 0 -> PENDIENTE
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    ADD COLUMN estado ENUM('PENDIENTE','ACTIVO','BLOQUEADO') NOT NULL DEFAULT 'PENDIENTE'
        AFTER rol;

UPDATE usuarios SET estado = IF(enabled = 1, 'ACTIVO', 'PENDIENTE');

-- Recién después de migrar los datos se elimina la columna vieja.
ALTER TABLE usuarios DROP COLUMN enabled;

-- ---------------------------------------------------------------------
-- 2. `username` único
--
-- Verificar primero que no haya repetidos; si los hay, resolverlos a mano
-- antes de crear el índice (el ALTER va a fallar):
--
--   SELECT username, COUNT(*) FROM usuarios
--    WHERE deleted_at IS NULL
--    GROUP BY username HAVING COUNT(*) > 1;
--
-- Si este ALTER falla, el paso 1 ya está aplicado y es correcto: corregir los duplicados y
-- ejecutar únicamente este ALTER.
--
-- Nota: el índice es global, no solo sobre los activos. Un username de una
-- cuenta dada de baja queda ocupado. Si hace falta reutilizarlo, aplicar el
-- patrón de columna generada que ya usan `stock` y `sesiones_caja`.
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    ADD UNIQUE KEY uk_usuarios_username (username);

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema='minimarket' AND table_name='usuarios'
        AND column_name='estado')                                  AS col_estado,
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema='minimarket' AND table_name='usuarios'
        AND column_name='enabled')                                 AS col_enabled_vieja,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema='minimarket' AND index_name='uk_usuarios_username') AS uk_username;

SELECT estado, COUNT(*) AS usuarios FROM usuarios GROUP BY estado;
