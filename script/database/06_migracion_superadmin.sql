-- =====================================================================
-- Migración — Rol SUPERADMIN
-- =====================================================================
-- Aplica sobre una base que ya tenga 05_migracion_estado_usuario.sql.
-- Si la base se crea desde cero, 00_init.sql y 01_seed.sql ya incluyen todo esto.
--
-- Uso:  mysql -u root -p < 06_migracion_superadmin.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- 1. `rol` acepta SUPERADMIN
--
-- Jerarquía SUPERADMIN > ADMIN > EMPLEADO: el superadmin administra el
-- sistema y gestiona a los ADMIN (alta, bloqueo y baja), cosa que un ADMIN
-- no puede hacer sobre otro ADMIN.
--
-- Agregar un valor al final del ENUM no reescribe los datos existentes:
-- ADMIN y EMPLEADO conservan su valor. Igual conviene correrlo con la app
-- detenida, como cualquier ALTER sobre `usuarios`.
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    MODIFY COLUMN rol ENUM('SUPERADMIN','ADMIN','EMPLEADO') NOT NULL;

-- ---------------------------------------------------------------------
-- 2. Alta del superadmin
--
-- Es el único camino para tener uno: la API no lo crea, porque ningún rol
-- manda sobre su propio nivel y un SUPERADMIN no puede dar de alta a otro.
--
-- email:    superadmin@minimarket.local
-- password: Super123!
--
-- **Cambiar la contraseña apenas se ingresa** (POST /api/users/v1/{id}/change-password).
-- Si preferís promover una cuenta que ya existe en lugar de crear una nueva,
-- saltear este INSERT y usar el UPDATE comentado más abajo.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT COLUMN_TYPE AS enum_rol
  FROM information_schema.columns
 WHERE table_schema = 'minimarket' AND table_name = 'usuarios' AND column_name = 'rol';

SELECT rol, estado, COUNT(*) AS usuarios
  FROM usuarios
 WHERE deleted_at IS NULL
 GROUP BY rol, estado;
