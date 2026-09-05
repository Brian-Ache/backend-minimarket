-- =====================================================================
-- Indice para el historial de cortes paginado
-- =====================================================================
-- GET /api/caja/v1/corte/historial pasa a devolver un Page: una fila por
-- turno cerrado crece para siempre, asi que traerlas todas en cada
-- request dejo de tener sentido.
--
-- La consulta filtra por estado y deleted_at, y ordena por fecha_cierre
-- descendente. El unico indice que la cubria era
-- ix_sesiones_estado_fecha (estado, deleted_at, created_at), que sirve
-- para el filtro pero no para ese orden: created_at es cuando se abrio
-- el turno y fecha_cierre cuando se cerro, y no son el mismo orden
-- —un turno puede abrirse antes que otro y cerrarse despues—. Sin el
-- indice, cada pagina ordena en memoria todo el historial filtrado para
-- devolver veinte filas.
--
-- El indice viejo se conserva: lo usan las otras consultas por estado,
-- que siguen ordenando por created_at.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 11_indice_cierre_sesiones.sql
-- =====================================================================

USE minimarket;

ALTER TABLE sesiones_caja
    ADD KEY ix_sesiones_estado_cierre (estado, deleted_at, fecha_cierre);

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS ix_estado_cierre
  FROM information_schema.statistics
 WHERE table_schema = 'minimarket'
   AND table_name = 'sesiones_caja'
   AND index_name = 'ix_sesiones_estado_cierre';
