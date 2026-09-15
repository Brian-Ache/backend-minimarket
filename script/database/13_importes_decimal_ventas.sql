-- =====================================================================
-- Los importes de ventas dejan de ser FLOAT
-- =====================================================================
-- FLOAT no representa la mayoria de los importes: 4850.10 se guarda como
-- el binario mas cercano, no como 4850.10. Mientras sumaba una sola
-- maquina el error quedaba escondido en el redondeo de la respuesta;
-- a partir del sync suman dos —el front arma el total del ticket y el
-- backend lo recalcula— y tienen que coincidir. Cada diferencia de
-- redondeo se presentaria como un ticket "para revisar" que en realidad
-- no tiene nada.
--
-- DECIMAL(12,2) guarda el numero tal cual, asi que la comparacion pasa
-- a ser exacta y no hace falta ninguna tolerancia. 12 digitos con 2
-- decimales llegan hasta 9.999.999.999,99: de sobra para el ticket mas
-- caro que este sistema vaya a ver.
--
-- Se migra tambien monto_recibido, que el plan no listaba. Es plata, es
-- de la misma tabla y entra en el mismo ALTER; dejarla en FLOAT
-- obligaria a convertir de ida y vuelta para calcular el vuelto y a
-- migrar dos veces la tabla que mas crece.
--
-- Los importes de compras, caja y productos siguen en FLOAT. Es la deuda
-- tecnica #1 del roadmap y no se salda aca: esta migracion cubre lo que
-- el sync necesita comparar contra un total ajeno.
--
-- QUE PASA CON LOS DATOS YA CARGADOS. La conversion toma el valor que el
-- FLOAT representa y lo redondea a dos decimales, que es la mejor
-- lectura posible de lo que se quiso guardar. Un total de 4850.0999 se
-- convierte en 4850.10. Las consultas informativas de abajo muestran
-- que se esta arrastrando ANTES de convertir: conviene mirarlas y
-- guardar su salida.
--
-- OJO CON ESTO, que es contraintuitivo: la cabecera y las lineas se
-- redondean por separado, asi que la conversion puede DESCUADRAR una
-- venta que en FLOAT cuadraba. Un total de 999.999 queda en 1000.00,
-- pero su linea de 3 x 333.333 queda en 3 x 333.33 = 999.99. La ultima
-- consulta de este script lista las ventas que terminaron asi.
--
-- Esas diferencias no se corrigen automaticamente. El total es la plata
-- que el cliente efectivamente pago y no se toca por un centavo de
-- redondeo; las lineas son el detalle de esa venta. Si alguna diferencia
-- es grande, no es redondeo y hay que mirarla a mano.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 13_importes_decimal_ventas.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Revisar primero: ventas cuyo total no coincide con la suma de sus
-- detalles. Son las que ya venian arrastrando error de redondeo, y
-- despues de la conversion no se van a poder distinguir de las demas.
-- Una diferencia de centavos es el FLOAT; una diferencia grande es otra
-- cosa y hay que mirarla.
-- ---------------------------------------------------------------------
SELECT BIN_TO_UUID(v.id, 0)                     AS venta,
       v.created_at,
       v.total                                  AS total_guardado,
       ROUND(SUM(d.precio_unitario * d.cantidad), 2) AS suma_detalles,
       ROUND(v.total - SUM(d.precio_unitario * d.cantidad), 4) AS diferencia
  FROM ventas v
  JOIN detalles_ventas d ON d.id_venta = v.id AND d.deleted_at IS NULL
 WHERE v.deleted_at IS NULL
 GROUP BY v.id, v.created_at, v.total
HAVING ABS(diferencia) > 0.005
 ORDER BY ABS(diferencia) DESC;

-- ---------------------------------------------------------------------
-- Revisar primero: importes que hoy tienen mas de dos decimales. Son los
-- que la conversion va a redondear.
-- ---------------------------------------------------------------------
SELECT 'ventas.total' AS columna, COUNT(*) AS filas_que_se_redondean
  FROM ventas
 WHERE total <> ROUND(total, 2)
UNION ALL
SELECT 'ventas.monto_recibido', COUNT(*)
  FROM ventas
 WHERE monto_recibido IS NOT NULL
   AND monto_recibido <> ROUND(monto_recibido, 2)
UNION ALL
SELECT 'detalles_ventas.precio_unitario', COUNT(*)
  FROM detalles_ventas
 WHERE precio_unitario <> ROUND(precio_unitario, 2)
UNION ALL
SELECT 'detalles_ventas.costo_unitario', COUNT(*)
  FROM detalles_ventas
 WHERE costo_unitario IS NOT NULL
   AND costo_unitario <> ROUND(costo_unitario, 2);

-- ---------------------------------------------------------------------
-- La conversion
-- ---------------------------------------------------------------------
ALTER TABLE ventas
    MODIFY COLUMN total          DECIMAL(12,2) NOT NULL,
    MODIFY COLUMN monto_recibido DECIMAL(12,2) NULL;

ALTER TABLE detalles_ventas
    MODIFY COLUMN precio_unitario DECIMAL(12,2) NOT NULL,
    MODIFY COLUMN costo_unitario  DECIMAL(12,2) NULL;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT table_name, column_name, column_type
  FROM information_schema.columns
 WHERE table_schema = 'minimarket'
   AND (   (table_name = 'ventas'          AND column_name IN ('total', 'monto_recibido'))
        OR (table_name = 'detalles_ventas' AND column_name IN ('precio_unitario', 'costo_unitario')))
 ORDER BY table_name, ordinal_position;

-- Despues de convertir, ninguna fila puede tener mas de dos decimales.
SELECT (SELECT COUNT(*) FROM ventas WHERE total <> ROUND(total, 2))
     + (SELECT COUNT(*) FROM detalles_ventas WHERE precio_unitario <> ROUND(precio_unitario, 2))
       AS filas_con_mas_de_dos_decimales;

-- ---------------------------------------------------------------------
-- Ventas que quedaron descuadradas DESPUES de convertir. Ahora la resta
-- es exacta, asi que lo que aparece aca es real: la mayoria van a ser
-- diferencias de uno o dos centavos que produjo el redondeo de esta
-- misma migracion —cabecera y lineas redondean por separado— y no hay
-- nada que hacer con ellas. Una diferencia grande es otra cosa.
-- ---------------------------------------------------------------------
SELECT BIN_TO_UUID(v.id, 0)                AS venta,
       v.created_at,
       v.total                             AS total_cobrado,
       SUM(d.precio_unitario * d.cantidad) AS suma_lineas,
       v.total - SUM(d.precio_unitario * d.cantidad) AS diferencia
  FROM ventas v
  JOIN detalles_ventas d ON d.id_venta = v.id AND d.deleted_at IS NULL
 WHERE v.deleted_at IS NULL
 GROUP BY v.id, v.created_at, v.total
HAVING diferencia <> 0
 ORDER BY ABS(diferencia) DESC;
