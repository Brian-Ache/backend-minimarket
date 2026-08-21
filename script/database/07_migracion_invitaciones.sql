-- =====================================================================
-- Migración — Alta por invitación
-- =====================================================================
-- Aplica sobre una base que ya tenga 06_migracion_superadmin.sql.
-- Si la base se crea desde cero, 00_init.sql ya incluye todo esto.
--
-- Uso:  mysql -u root -p < 07_migracion_invitaciones.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- `token_type` acepta INVITATION
--
-- El alta por invitación necesita un tipo propio y no puede reusar
-- VERIFICATION: con el token de verificación el usuario solo confirma su
-- email —la contraseña ya la había elegido él—, mientras que con el de
-- invitación la define por primera vez. Si compartieran tipo, un token de
-- verificación serviría para setear la contraseña de esa cuenta.
--
-- Agregar un valor al ENUM no reescribe los datos existentes.
-- ---------------------------------------------------------------------
ALTER TABLE auth_tokens
    MODIFY COLUMN token_type ENUM('PASSWORD_RESET','VERIFICATION','INVITATION') NOT NULL;

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT COLUMN_TYPE AS enum_token_type
  FROM information_schema.columns
 WHERE table_schema = 'minimarket'
   AND table_name = 'auth_tokens'
   AND column_name = 'token_type';
