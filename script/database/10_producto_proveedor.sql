-- =====================================================================
-- Precio de referencia de cada proveedor por producto
-- =====================================================================
-- productos.id_proveedor guarda UN proveedor, el habitual, y el unico
-- precio por proveedor que quedaba registrado era el precio_unitario de
-- cada detalles_compras: lo que se pago esa vez, no lo que el proveedor
-- lista. Al momento de comprar no habia con que comparar.
--
-- Esta tabla es el catalogo de referencia: un precio por par
-- (producto, proveedor), cargado y actualizado siempre a mano. No influye
-- en ninguna compra ni en ningun calculo: es un dato de consulta, y el
-- criterio de a quien comprarle sigue siendo del usuario.
--
-- productos.id_proveedor NO se toca. Lo usan el catalogo, el filtro por
-- proveedor y los reportes; sacarlo era un cambio incompatible a cambio
-- de nada, porque "el proveedor habitual" y "los precios que conozco"
-- son dos datos distintos.
--
-- precio_referencia es DECIMAL y no FLOAT, a diferencia del resto de los
-- importes del sistema. Esa es deuda conocida (ver ARCHITECTURE.md); una
-- columna nueva no tiene por que nacer con el problema, y esta no se
-- suma con ninguna otra: es un valor que se muestra.
--
-- La unicidad va sobre una columna generada y no sobre id_proveedor
-- directamente, mismo patron que uk_stock_producto_activo y
-- uk_productos_barcode_activo: un producto tiene un solo precio ACTIVO
-- por proveedor, y borrar la referencia libera el par para volver a
-- cargarlo mas adelante.
--
-- Aplicar una sola vez, con la aplicacion detenida.
-- Uso: mysql -u root -p < 10_producto_proveedor.sql
-- =====================================================================

USE minimarket;

CREATE TABLE IF NOT EXISTS producto_proveedor (
    id                BINARY(16)    NOT NULL,
    id_producto       BINARY(16)    NOT NULL,
    id_proveedor      BINARY(16)    NOT NULL,
    precio_referencia DECIMAL(12,2) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    deleted_at        DATETIME(6)   NULL,
    proveedor_activo  BINARY(16)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, id_proveedor, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prod_prov_activo (id_producto, proveedor_activo),
    KEY ix_prod_prov_producto (id_producto, deleted_at),
    KEY ix_prod_prov_proveedor (id_proveedor, deleted_at),
    CONSTRAINT fk_prod_prov_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_prov_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedores (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = 'minimarket' AND table_name = 'producto_proveedor') AS tabla,
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = 'minimarket'
        AND index_name = 'uk_prod_prov_activo') AS uk_precio_activo;
