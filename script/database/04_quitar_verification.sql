-- =====================================================================
-- Baja del tipo de token VERIFICATION
-- =====================================================================
-- El circuito de verificacion de email quedo sin emisor cuando se elimino
-- el autorregistro: ningun codigo llamaba ya a generateVerificationToken,
-- asi que verifyEmail validaba tokens que el sistema nunca creaba. Se
-- podo el flujo entero del backend y esta migracion acompana ese cambio.
--
-- IMPORTANTE: aplicar ANTES de desplegar la version que quita el valor.
-- El enum de Java ya no conoce VERIFICATION, y una fila con ese valor
-- revienta al leerse (IllegalArgumentException de Hibernate al mapear).
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 04_quitar_verification.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Cuantas filas historicas hay (informativo, correr antes de borrar)
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS tokens_verification_a_borrar
  FROM auth_tokens
 WHERE token_type = 'VERIFICATION';

-- ---------------------------------------------------------------------
-- Baja de las filas y del valor del enum
-- ---------------------------------------------------------------------
-- Son tokens de un flujo que ya no existe: usados o no, no los puede
-- consumir nadie. No hay dato que preservar.
DELETE FROM auth_tokens
 WHERE token_type = 'VERIFICATION';

ALTER TABLE auth_tokens
    MODIFY COLUMN token_type ENUM('PASSWORD_RESET','INVITATION') NOT NULL;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM auth_tokens WHERE token_type = 'VERIFICATION') AS filas_restantes,
    (SELECT COLUMN_TYPE FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'auth_tokens'
        AND column_name = 'token_type') AS enum_actual;
