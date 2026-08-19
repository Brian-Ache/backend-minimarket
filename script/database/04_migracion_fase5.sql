-- =====================================================================
-- Migración — Fase 5 del plan de corrección de bugs
-- =====================================================================
-- Aplica sobre una base que ya tenga la migración de la Fase 4.
-- Si la base se crea desde cero, 00_init.sql ya incluye todo esto.
--
-- Uso:  mysql -u root -p < 04_migracion_fase5.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- B-25 · Nombre de tabla en plural
-- `RefreshToken` era la única entidad sin @Table, así que Hibernate derivaba
-- `refresh_token` en singular mientras el resto del esquema usa plural.
-- Ahora la entidad lo declara explícitamente y la tabla se renombra.
--
-- Efecto colateral: renombrar invalida las sesiones abiertas solo si se
-- corre con la app arriba. Pararla antes, o avisar que hay que reloguear.
-- ---------------------------------------------------------------------
RENAME TABLE refresh_token TO refresh_tokens;

ALTER TABLE refresh_tokens
    RENAME INDEX uk_refresh_token_token_hash TO uk_refresh_tokens_token_hash,
    RENAME INDEX ix_refresh_token_user_activo TO ix_refresh_tokens_user_activo;

-- La clave foránea conserva su nombre viejo; renombrarla exige recrearla.
-- Es cosmético, así que se hace aparte y se puede omitir.
ALTER TABLE refresh_tokens DROP FOREIGN KEY fk_refresh_token_usuario;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema='minimarket' AND table_name='refresh_tokens') AS tabla_plural,
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema='minimarket' AND table_name='refresh_token')  AS tabla_vieja;
