-- =====================================================================
-- Ventas sincronizadas desde el front offline
-- =====================================================================
-- El front pasa a crear tickets sin conexion y a mandarlos por lote
-- cuando vuelve internet. Eso deja rastros que hoy la tabla no puede
-- guardar, y que son los que permiten auditar despues lo que entro por
-- ese camino:
--
--   sincronizado_en    cuando el ticket llego a MySQL. NULL en las
--                      ventas online, que llegan en el momento.
--                      created_at pasa a significar cuando ocurrio el
--                      ticket, que es lo que los reportes siempre
--                      asumieron; esta columna es el dato nuevo.
--   origen             ONLINE u OFFLINE. Es lo que permite mirar
--                      aparte lo que entro por el endpoint de sync.
--   dispositivo        que caja lo genero ("caja-01"). Viene en el lote.
--   id_usuario_sync    quien sincronizo, tomado del JWT. No es el
--                      vendedor: el vendedor viaja en el payload porque
--                      es un hecho de hace dos dias, y el par de los dos
--                      es lo que deja a la vista quien atribuyo que.
--   requiere_revision  el ticket se guardo pero dejo una discrepancia:
--                      stock que hubo que regularizar, o un total
--                      declarado que no coincidio con el recalculado.
--
-- El uuid del ticket lo genera el front y se guarda en ventas.id, la
-- columna BINARY(16) que ya es PK. No se agrega un segundo
-- identificador: el formato pasa a ser UUIDv7, cuyos 48 bits altos son
-- el timestamp, asi que las inserciones vuelven a ser casi secuenciales
-- y el indice no se fragmenta como lo haria con un v4.
--
-- anulaciones_pendientes resuelve el caso de un ANULAR que llega antes
-- que el CREAR de su propio ticket —el lote se corto entre los dos—.
-- La fila espera ahi hasta que el ticket llegue. Va sin FK a ventas a
-- proposito: la venta puede no existir todavia, que es justamente la
-- unica razon por la que la tabla existe.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 12_ventas_offline.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Informativo: cuantas ventas hay hoy. Todas quedan con origen ONLINE,
-- que es lo que efectivamente son: entraron por POST /api/ventas/v1.
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS ventas_existentes,
       SUM(deleted_at IS NULL) AS vigentes
  FROM ventas;

-- ---------------------------------------------------------------------
-- Columnas nuevas de ventas
-- ---------------------------------------------------------------------
ALTER TABLE ventas
    ADD COLUMN sincronizado_en   DATETIME(6) NULL AFTER deleted_at,
    ADD COLUMN origen            VARCHAR(10) NOT NULL DEFAULT 'ONLINE' AFTER sincronizado_en,
    ADD COLUMN dispositivo       VARCHAR(50) NULL AFTER origen,
    ADD COLUMN id_usuario_sync   BINARY(16)  NULL AFTER dispositivo,
    ADD COLUMN requiere_revision BIT(1)      NOT NULL DEFAULT b'0' AFTER id_usuario_sync;

-- El listado de revision filtra por las dos, y es el punto de entrada al
-- trabajo que deja una venta offline con discrepancias.
ALTER TABLE ventas
    ADD KEY ix_ventas_revision (requiere_revision, deleted_at);

ALTER TABLE ventas
    ADD CONSTRAINT fk_ventas_usuario_sync
        FOREIGN KEY (id_usuario_sync) REFERENCES usuarios (id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------
-- Anulaciones que llegaron antes que su venta
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS anulaciones_pendientes (
    id_venta   BINARY(16)   NOT NULL,
    anulado_en DATETIME(6)  NOT NULL,
    id_usuario BINARY(16)   NOT NULL,
    motivo     VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id_venta),
    CONSTRAINT fk_anulaciones_pend_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = 'minimarket'
   AND table_name = 'ventas'
   AND column_name IN ('sincronizado_en', 'origen', 'dispositivo',
                       'id_usuario_sync', 'requiere_revision')
 ORDER BY ordinal_position;

SELECT COUNT(DISTINCT index_name) AS ix_ventas_revision
  FROM information_schema.statistics
 WHERE table_schema = 'minimarket'
   AND table_name = 'ventas'
   AND index_name = 'ix_ventas_revision';

SELECT COUNT(*) AS tabla_anulaciones_pendientes
  FROM information_schema.tables
 WHERE table_schema = 'minimarket'
   AND table_name = 'anulaciones_pendientes';

-- Tiene que dar 0: ninguna venta anterior a esta migracion entro por el
-- endpoint de sync.
SELECT COUNT(*) AS ventas_offline_preexistentes
  FROM ventas
 WHERE origen <> 'ONLINE';
