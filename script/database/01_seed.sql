-- =====================================================================
-- backend-minimarket — Datos iniciales (opcional, para desarrollo)
-- =====================================================================
-- Requiere haber ejecutado 00_init.sql.
-- Uso:  mysql -u root -p < 01_seed.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Superadministrador — llave maestra del sistema
-- email:    superadmin@minimarket.local
-- password: Super123!
--
-- Es el único camino para tener un SUPERADMIN: no hay endpoint que lo cree, porque ningún rol
-- manda sobre su propio nivel. **Cambiar esta contraseña antes de exponer el sistema.**
-- ---------------------------------------------------------------------
SET @id_superadmin = UUID_TO_BIN('11111111-1111-4111-8111-111111111100', 0);

INSERT INTO usuarios (id, nombre, apellido, username, email, hash_password, rol, estado, created_at, updated_at)
VALUES (
    @id_superadmin, 'Super', 'Admin', 'superadmin', 'superadmin@minimarket.local',
    '$2a$10$th8pTK4hJAP0kr9Dzh/Jh.RyVhLMd4SkmREGeImqB3F/lYOuEw6ky',
    'SUPERADMIN', 'ACTIVO', NOW(6), NOW(6)
)
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Usuario administrador
-- email:    admin@minimarket.local
-- password: Admin123!
-- estado = ACTIVO porque el login exige una cuenta con acceso.
-- ---------------------------------------------------------------------
SET @id_admin = UUID_TO_BIN('11111111-1111-4111-8111-111111111111', 0);

INSERT INTO usuarios (id, nombre, apellido, username, email, hash_password, rol, estado, created_at, updated_at)
VALUES (
    @id_admin, 'Admin', 'Principal', 'admin', 'admin@minimarket.local',
    '$2a$10$VJX/uuHg2XAXjoSo7yg9qeSaYnJBh3lwZeIWOhltr8RIBVmPPqRaK',
    'ADMIN', 'ACTIVO', NOW(6), NOW(6)
)
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Categorías
-- ---------------------------------------------------------------------
SET @cat_bebidas   = UUID_TO_BIN('22222222-2222-4222-8222-222222222201', 0);
SET @cat_almacen   = UUID_TO_BIN('22222222-2222-4222-8222-222222222202', 0);
SET @cat_lacteos   = UUID_TO_BIN('22222222-2222-4222-8222-222222222203', 0);
SET @cat_limpieza  = UUID_TO_BIN('22222222-2222-4222-8222-222222222204', 0);

INSERT INTO categorias (id, nombre, descripcion, created_at, updated_at) VALUES
    (@cat_bebidas,  'Bebidas',  'Gaseosas, aguas, jugos y cervezas',   NOW(6), NOW(6)),
    (@cat_almacen,  'Almacén',  'Secos, conservas y panificados',      NOW(6), NOW(6)),
    (@cat_lacteos,  'Lácteos',  'Leche, yogur, quesos (con lote)',     NOW(6), NOW(6)),
    (@cat_limpieza, 'Limpieza', 'Artículos de limpieza y perfumería',  NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Proveedores
-- ---------------------------------------------------------------------
SET @prov_dist   = UUID_TO_BIN('33333333-3333-4333-8333-333333333301', 0);
SET @prov_lacteo = UUID_TO_BIN('33333333-3333-4333-8333-333333333302', 0);

INSERT INTO proveedores (id, nombre, telefono, email, direccion, created_at, updated_at) VALUES
    (@prov_dist,   'Distribuidora Central', '+54 11 4000-1000', 'ventas@distcentral.local', 'Av. Siempreviva 742', NOW(6), NOW(6)),
    (@prov_lacteo, 'Lácteos del Sur',       '+54 11 4000-2000', 'pedidos@lacteosur.local',  'Ruta 3 km 45',        NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Productos
-- maneja_lotes = 1 => el stock se descuenta por lote (FIFO por vencimiento)
--                     y NO se usa la tabla `stock`.
-- ---------------------------------------------------------------------
SET @prod_gaseosa = UUID_TO_BIN('44444444-4444-4444-8444-444444444401', 0);
SET @prod_fideos  = UUID_TO_BIN('44444444-4444-4444-8444-444444444402', 0);
SET @prod_leche   = UUID_TO_BIN('44444444-4444-4444-8444-444444444403', 0);
SET @prod_lavandi = UUID_TO_BIN('44444444-4444-4444-8444-444444444404', 0);

INSERT INTO productos (id, nombre, barcode, precio, costo, margen, maneja_lotes, id_categoria, id_proveedor, created_at, updated_at) VALUES
    (@prod_gaseosa, 'Gaseosa Cola 2.25L',   '7790001000019', 2500, 1800, 38.9, b'0', @cat_bebidas,  @prov_dist,   NOW(6), NOW(6)),
    (@prod_fideos,  'Fideos Guiseros 500g', '7790001000026',  950,  620, 53.2, b'0', @cat_almacen,  @prov_dist,   NOW(6), NOW(6)),
    (@prod_leche,   'Leche Entera 1L',      '7790001000033', 1400, 1050, 33.3, b'1', @cat_lacteos,  @prov_lacteo, NOW(6), NOW(6)),
    (@prod_lavandi, 'Lavandina 1L',         '7790001000040',  890,  560, 58.9, b'0', @cat_limpieza, @prov_dist,   NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Stock agregado (solo productos sin lotes)
-- ---------------------------------------------------------------------
INSERT INTO stock (id, id_producto, cantidad, created_at, updated_at) VALUES
    (UUID_TO_BIN('55555555-5555-4555-8555-555555555501', 0), @prod_gaseosa, 48, NOW(6), NOW(6)),
    (UUID_TO_BIN('55555555-5555-4555-8555-555555555502', 0), @prod_fideos,  120, NOW(6), NOW(6)),
    (UUID_TO_BIN('55555555-5555-4555-8555-555555555503', 0), @prod_lavandi, 30, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Lotes (producto con maneja_lotes = 1)
-- ---------------------------------------------------------------------
INSERT INTO lote (id, id_producto, numero_lote, estado, fecha_vencimiento, cantidad, created_at, updated_at) VALUES
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666601', 0), @prod_leche, 'L-2024-A', 'VIGENTE', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 24, NOW(6), NOW(6)),
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666602', 0), @prod_leche, 'L-2024-B', 'PROXIMO', DATE_ADD(CURDATE(), INTERVAL  5 DAY), 12, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Verificación rápida
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM usuarios)    AS usuarios,
    (SELECT COUNT(*) FROM categorias)  AS categorias,
    (SELECT COUNT(*) FROM proveedores) AS proveedores,
    (SELECT COUNT(*) FROM productos)   AS productos,
    (SELECT COUNT(*) FROM stock)       AS stock,
    (SELECT COUNT(*) FROM lote)        AS lotes;
