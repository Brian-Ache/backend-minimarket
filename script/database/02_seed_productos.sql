-- =====================================================================
-- backend-minimarket — Seed de productos (100 productos para desarrollo)
-- =====================================================================
-- Requiere haber ejecutado 00_init.sql y 01_seed.sql.
-- Uso:  mysql -u root -p < 02_seed_productos.sql
-- =====================================================================

USE minimarket;

-- ---------------------------------------------------------------------
-- Categorias (variables para referenciar)
-- ---------------------------------------------------------------------
SET @cat_bebidas   = UUID_TO_BIN('22222222-2222-4222-8222-222222222201', 0);
SET @cat_almacen   = UUID_TO_BIN('22222222-2222-4222-8222-222222222202', 0);
SET @cat_lacteos   = UUID_TO_BIN('22222222-2222-4222-8222-222222222203', 0);
SET @cat_limpieza  = UUID_TO_BIN('22222222-2222-4222-8222-222222222204', 0);

-- ---------------------------------------------------------------------
-- Proveedores (variables para referenciar)
-- ---------------------------------------------------------------------
SET @prov_dist   = UUID_TO_BIN('33333333-3333-4333-8333-333333333301', 0);
SET @prov_lacteo = UUID_TO_BIN('33333333-3333-4333-8333-333333333302', 0);

-- =====================================================================
-- PRODUCTOS (100 productos)
-- =====================================================================

-- ---------------------------------------------------------------------
-- BEBIDAS (25 productos)
-- ---------------------------------------------------------------------
INSERT INTO productos (id, nombre, barcode, precio, costo, margen, maneja_lotes, id_categoria, id_proveedor, created_at, updated_at) VALUES
(UUID_TO_BIN('44444444-4444-4444-8444-444444444405', 0), 'Coca Cola Original 2.25L',            '7790001000056', 3200, 2200, 45.5, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444406', 0), 'Coca Cola Sin Azucar 2.25L',          '7790001000063', 3100, 2150, 44.2, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444407', 0), 'Sprite Lima Limon 2.25L',             '7790001000070', 3000, 2100, 42.9, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444408', 0), 'Fanta Naranja 2.25L',                 '7790001000087', 2900, 2000, 45.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444409', 0), 'Coca Cola Original 500ml',            '7790001000094', 1400, 900, 55.6, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444410', 0), 'Pepsi Black 1.5L',                    '7790001000100', 2100, 1500, 40.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444411', 0), 'Agua Mineral Villavicencio 1.5L',     '7790001000117', 1200, 700, 71.4, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444412', 0), 'Agua Saborizada Leprite Naranja 1.5L','7790001000124', 1600, 1000, 60.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444413', 0), 'Cerveza Quilmes Clasica 1L',          '7790001000131', 2500, 1800, 38.9, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444414', 0), 'Cerveza Stella Artois Lata 473ml',    '7790001000148', 1900, 1300, 46.2, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444415', 0), 'Fernet Branca 750ml',                 '7790001000155', 11500, 8500, 35.3, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444416', 0), 'Jugo DelValle Naranja 1L',            '7790001000162', 1500, 900, 66.7, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444417', 0), 'Agua Tonica Schweppes 1.5L',          '7790001000179', 1800, 1200, 50.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444418', 0), 'Gaseosa Pomelo Paso de los Toros 1.5L','7790001000186', 1700, 1100, 54.5, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444419', 0), 'Energizante Red Bull 473ml',           '7790001000193', 2800, 1900, 47.4, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444420', 0), 'Cerveza Patagonia Amber Lager 473ml', '7790001000209', 2200, 1500, 46.7, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444421', 0), 'Cerveza IPA Brutal Artisan 473ml',     '7790001000216', 3000, 2000, 50.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444422', 0), 'Sidra 1888 Premium 750ml',            '7790001000223', 3500, 2400, 45.8, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444423', 0), 'Vino Portillo Malbec 750ml',          '7790001000230', 2800, 1800, 55.6, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444424', 0), 'Vino Santa Julia Chenin Dulce 750ml', '7790001000247', 2400, 1500, 60.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444425', 0), 'Licor Baileys Original 700ml',        '7790001000254', 8500, 6000, 41.7, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444426', 0), 'Gin Gordon's London Dry 700ml',       '7790001000261', 7200, 5000, 44.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444427', 0), 'Whisky Johnnie Walker Red 750ml',     '7790001000278', 9800, 7000, 40.0, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444428', 0), 'Tequila Jose Cuervo Especial 700ml',  '7790001000285', 8200, 5800, 41.4, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444429', 0), 'Agua de Coco CocoMix 1L',             '7790001000292', 1900, 1200, 58.3, b'0', @cat_bebidas, @prov_dist, NOW(6), NOW(6));

-- ---------------------------------------------------------------------
-- ALMACEN (35 productos)
-- ---------------------------------------------------------------------
INSERT INTO productos (id, nombre, barcode, precio, costo, margen, maneja_lotes, id_categoria, id_proveedor, created_at, updated_at) VALUES
(UUID_TO_BIN('44444444-4444-4444-8444-444444444430', 0), 'Fideos Matarazzo Tallarin 500g',      '7790001000308', 1300, 800, 62.5, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444431', 0), 'Fideos Lucchetti Monitos 500g',       '7790001000315', 1200, 750, 60.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444432', 0), 'Arroz Lucchetti Parboil 1kg',         '7790001000322', 2100, 1400, 50.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444433', 0), 'Aceite de Girasol Natura 900ml',      '7790001000339', 2200, 1500, 46.7, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444434', 0), 'Aceite de Oliva Canuelas 500ml',      '7790001000346', 6800, 4800, 41.7, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444435', 0), 'Harina de Trigo Favorita 0000 1kg',   '7790001000353', 1100, 700, 57.1, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444436', 0), 'Azucar Ledesma Clasica 1kg',          '7790001000360', 1250, 800, 56.3, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444437', 0), 'Sal Fina Celusal 500g',               '7790001000377', 950, 500, 90.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444438', 0), 'Pure de Tomate Noel 520g',            '7790001000384', 800, 500, 60.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444439', 0), 'Yerba Mate Playadito 500g',           '7790001000391', 2900, 2000, 45.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444440', 0), 'Yerba Mate Taragui Con Palo 500g',    '7790001000407', 2700, 1800, 50.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444441', 0), 'Cafe Instantaneo Dolca 170g',         '7790001000414', 5400, 3800, 42.1, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444442', 0), 'Cafe Molido La Virginia 250g',        '7790001000421', 4200, 2800, 50.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444443', 0), 'Te Taragui Saquitos 25u',             '7790001000438', 900, 500, 80.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444444', 0), 'Mermelada Arcor Frutilla 390g',       '7790001000445', 2200, 1400, 57.1, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444445', 0), 'Dulce de Leche La Serenisima 400g',   '7790001000452', 2600, 1700, 52.9, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444446', 0), 'Mayonesa Natura Doypack 500g',        '7790001000469', 1850, 1200, 54.2, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444447', 0), 'Ketchup Hellmanns Doypack 250g',      '7790001000476', 1500, 900, 66.7, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444448', 0), 'Mostaza Savora Doypack 250g',         '7790001000483', 1200, 700, 71.4, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444449', 0), 'Salsa de Soja Dos Anclas 200ml',      '7790001000490', 1750, 1000, 75.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444450', 0), 'Galletitas Oreo Original 118g',       '7790001000506', 1400, 800, 75.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444451', 0), 'Galletitas Criollitas 3x100g',        '7790001000513', 1600, 1000, 60.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444452', 0), 'Galletitas Chocolinas 170g',          '7790001000520', 1250, 750, 66.7, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444453', 0), 'Galletitas Pepitos 119g',             '7790001000537', 1350, 800, 68.8, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444454', 0), 'Galletitas Traviata 101g',            '7790001000544', 850, 500, 70.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444455', 0), 'Pan Lactal Blanco Mesa 560g',         '7790001000551', 2600, 1700, 52.9, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444456', 0), 'Tostadas Riera Clasicas 200g',        '7790001000568', 1450, 900, 61.1, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444457', 0), 'Cacao en Polvo Nesquik 300g',         '7790001000575', 2800, 1800, 55.6, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444458', 0), 'Harina Leudante Pureza 1kg',          '7790001000582', 1400, 900, 55.6, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444459', 0), 'Leche Entera La Serenisima 1L',       '7790001000599', 1350, 1000, 35.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444460', 0), 'Leche Descremada Ilolay 1L',          '7790001000605', 1400, 1050, 33.3, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444461', 0), 'Yogur Bebible Milkaut Frutilla 1kg',  '7790001000612', 2100, 1400, 50.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444462', 0), 'Manteca La Serenisima 200g',          '7790001000629', 2800, 2000, 40.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444463', 0), 'Crema de Leche Milkaut 200ml',        '7790001000636', 2300, 1500, 53.3, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444464', 0), 'Queso Crema Finlandia 300g',          '7790001000643', 3600, 2400, 50.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444465', 0), 'Mate Cocido Taragui Saquitos 25u',    '7790001000650', 880, 500, 76.0, b'0', @cat_almacen, @prov_dist, NOW(6), NOW(6));

-- ---------------------------------------------------------------------
-- LIMPIEZA (20 productos)
-- ---------------------------------------------------------------------
INSERT INTO productos (id, nombre, barcode, precio, costo, margen, maneja_lotes, id_categoria, id_proveedor, created_at, updated_at) VALUES
(UUID_TO_BIN('44444444-4444-4444-8444-444444444466', 0), 'Papel Higienico Higienol 4 Rullos',   '7790001000667', 2900, 1800, 61.1, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444467', 0), 'Rollos Cocina Sussex 3 Rollos',       '7790001000674', 2500, 1500, 66.7, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444468', 0), 'Detergente Magistral Limon 300ml',    '7790001000681', 2100, 1300, 61.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444469', 0), 'Lavandina Ayudin Clasica 1L',         '7790001000698', 1100, 600, 83.3, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444470', 0), 'Limpiador de Pisos Poett 900ml',      '7790001000704', 1400, 800, 75.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444471', 0), 'Jabon En Polvo Ala 800g',             '7790001000711', 2800, 1800, 55.6, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444472', 0), 'Suavizante Vivere Clasico 900ml',     '7790001000728', 2400, 1500, 60.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444473', 0), 'Esponja de Bronce Virulana 1u',       '7790001000735', 850, 400, 112.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444474', 0), 'Bolsas de Residuos 45x60 10u',        '7790001000742', 1100, 600, 83.3, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444475', 0), 'Fosforos Tres Estrellas 220u',        '7790001000759', 750, 400, 87.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444476', 0), 'Desodorante Ambiente Glade 360ml',    '7790001000766', 2200, 1400, 57.1, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444477', 0), 'Jabon de Glicerina Lapeche 3x90g',   '7790001000773', 1650, 1000, 65.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444478', 0), 'Shampoo Sedal Restauracion 340ml',    '7790001000780', 3100, 2000, 55.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444479', 0), 'Acondicionador Sedal Restauracion 340ml','7790001000797', 3100, 2000, 55.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444480', 0), 'Desodorante Axe Black 150ml',         '7790001000803', 3400, 2200, 54.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444481', 0), 'Desodorante Rexona Crema 60g',        '7790001000810', 2200, 1300, 69.2, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444482', 0), 'Crema Dental Colgate Total 12 90g',   '7790001000827', 2600, 1600, 62.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444483', 0), 'Jabon de Tocador Rexona Fresh 3x90g', '7790001000834', 1950, 1200, 62.5, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444484', 0), 'Protectores Diarios Nosotras 20u',    '7790001000841', 1800, 1000, 80.0, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6)),
(UUID_TO_BIN('44444444-4444-4444-8444-444444444485', 0), 'Lavandina Plus Lalanne 1L',           '7790001000858', 1200, 700, 71.4, b'0', @cat_limpieza, @prov_dist, NOW(6), NOW(6));
