-- =====================================================================
-- Retiro al cerrar el turno y saldo que queda en la caja
-- =====================================================================
-- El resumen diario sumaba el saldo de apertura de todas las sesiones del
-- dia. Eso solo seria correcto si al cerrar cada turno se retirara todo y
-- el siguiente arrancara con un fondo nuevo. Como en la practica queda
-- plata en la caja y el turno siguiente abre declarando lo que hay, la
-- misma plata se contaba una vez por turno y el saldo esperado del dia
-- salia inflado.
--
-- Se modela lo que realmente pasa al cerrar: una parte se retira y otra
-- queda. El retiro pasa a ser un movimiento de caja de tipo SALIDA con
-- origen RETIRO, con lo cual:
--   * el saldo esperado del turno sigue siendo el de antes de retirar,
--     que es lo que el cajero cuenta;
--   * el dia cierra con saldo_inicial del PRIMER turno + entradas -
--     salidas (retiros incluidos), sin volver a sumar lo que quedo en la
--     caja y el turno siguiente declaro como apertura.
--
-- diferencia_apertura guarda cuanto se aparto lo contado al abrir de lo
-- que habia dejado el cierre anterior: no bloquea la apertura —el
-- comercio tiene que poder abrir— pero deja el faltante o el sobrante
-- registrado en vez de perderlo.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 08_retiro_de_cierre_de_caja.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Turnos ya cerrados (informativo): quedan con monto_retirado y
-- saldo_dejado en NULL, que es "no se sabe" y no "no se retiro nada".
-- El resumen diario de esos dias sigue calculandose como antes.
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS turnos_cerrados_sin_retiro
  FROM sesiones_caja
 WHERE estado = 'CERRADA' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Columnas nuevas
-- ---------------------------------------------------------------------
ALTER TABLE sesiones_caja
    ADD COLUMN monto_retirado      FLOAT NULL AFTER diferencia,
    ADD COLUMN saldo_dejado        FLOAT NULL AFTER monto_retirado,
    ADD COLUMN diferencia_apertura FLOAT NULL AFTER saldo_dejado;

-- ---------------------------------------------------------------------
-- El retiro es un origen mas de movimiento de caja
-- ---------------------------------------------------------------------
ALTER TABLE movimientos_caja
    DROP CHECK ck_mov_caja_origen;

ALTER TABLE movimientos_caja
    ADD CONSTRAINT ck_mov_caja_origen
        CHECK (origen IS NULL OR origen IN ('MANUAL','VENTA','COMPRA','REVERSA','RETIRO'));

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = 'minimarket' AND table_name = 'sesiones_caja'
        AND column_name IN ('monto_retirado','saldo_dejado','diferencia_apertura')) AS columnas_nuevas,
    (SELECT CHECK_CLAUSE FROM information_schema.check_constraints
      WHERE constraint_schema = 'minimarket'
        AND constraint_name = 'ck_mov_caja_origen') AS check_origen;
