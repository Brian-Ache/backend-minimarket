-- =====================================================================
-- backend-minimarket — Esquema y datos iniciales
-- =====================================================================
-- Archivo unico y autocontenido: crea la base, el esquema completo y el
-- seed minimo con el que la aplicacion arranca. Una instalacion nueva no
-- necesita nada mas.
--
-- Es idempotente: las tablas se crean con IF NOT EXISTS y las filas del
-- seed con ON DUPLICATE KEY, asi que volver a correrlo sobre una base ya
-- creada no rompe ni pisa datos.
--
-- Convenciones del dominio:
--   * Soft delete: deleted_at NULL = registro activo.
--   * Auditoria: created_at / updated_at (@CreationTimestamp / @UpdateTimestamp).
--   * Los ids son BINARY(16); se leen con BIN_TO_UUID(id, 0).
--   * La unicidad "solo entre activos" se resuelve con una columna
--     generada que vale NULL cuando la fila esta borrada: MySQL no hace
--     chocar los NULL de un indice unico, asi que dar de baja una fila
--     libera su nombre, su barcode o su comprobante.
--
-- Uso: mysql -u root -p < init.sql
--
-- Los datos de demo (100 productos con su stock) van aparte, en
-- seed_demo.sql, y son opcionales.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS minimarket
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE minimarket;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 1. USUARIOS Y AUTENTICACION
-- =====================================================================

CREATE TABLE IF NOT EXISTS usuarios (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(50)  NOT NULL,
    apellido        VARCHAR(50)  NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL,
    hash_password   VARCHAR(255) NOT NULL,
    rol             ENUM('SUPERADMIN','ADMIN','EMPLEADO') NOT NULL,
    estado          ENUM('PENDIENTE','ACTIVO','BLOQUEADO') NOT NULL DEFAULT 'PENDIENTE',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_email (email),
    UNIQUE KEY uk_usuarios_username (username),
    KEY ix_usuarios_deleted_at (deleted_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS auth_tokens (
    id              BINARY(16)  NOT NULL,
    token_type      ENUM('PASSWORD_RESET','INVITATION') NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    used            BIT(1)      NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_tokens_token_hash (token_hash),
    KEY ix_auth_tokens_user_tipo_used (user_id, token_type, used),
    CONSTRAINT fk_auth_tokens_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BINARY(16)  NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    is_active       BIT(1)      NOT NULL DEFAULT b'1',
    revoked_at      DATETIME(6) NULL,
    expires_at      DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
    KEY ix_refresh_tokens_user_activo (user_id, is_active),
    CONSTRAINT fk_refresh_tokens_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- =====================================================================
-- 2. CATALOGO: CATEGORIAS, PROVEEDORES, PRODUCTOS
-- =====================================================================

CREATE TABLE IF NOT EXISTS categorias (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    descripcion     VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    -- El nombre es unico solo entre las categorias activas: los NULL de un
    -- indice unico no chocan entre si, asi que una categoria dada de baja
    -- libera su nombre y se puede volver a crear. Mismo patron que
    -- uk_productos_barcode_activo.
    nombre_activo   VARCHAR(100)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, nombre, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categorias_nombre_activo (nombre_activo),
    KEY ix_categorias_nombre (nombre),
    KEY ix_categorias_deleted_at (deleted_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS proveedores (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    telefono        VARCHAR(50)  NULL,
    email           VARCHAR(100) NULL,
    direccion       VARCHAR(255) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_proveedores_deleted_at (deleted_at),
    KEY ix_proveedores_nombre (nombre)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS productos (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(255) NULL,
    barcode         VARCHAR(255) NULL,
    precio          FLOAT        NOT NULL,
    costo           FLOAT        NULL,
    margen          FLOAT        NULL,
    maneja_lotes    BIT(1)       NOT NULL DEFAULT b'0',
    id_categoria    BINARY(16)   NULL,
    id_proveedor    BINARY(16)   NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    -- El barcode es unico solo entre los productos activos: los NULL de un
    -- indice unico no chocan entre si, asi que las filas borradas liberan el
    -- codigo. Mismo patron que uk_stock_producto_activo.
    barcode_activo  VARCHAR(255)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, barcode, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_productos_barcode_activo (barcode_activo),
    KEY ix_productos_barcode (barcode),
    KEY ix_productos_categoria (id_categoria, deleted_at),
    KEY ix_productos_proveedor (id_proveedor, deleted_at),
    KEY ix_productos_deleted_at (deleted_at),
    CONSTRAINT fk_productos_categoria
        FOREIGN KEY (id_categoria) REFERENCES categorias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_productos_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedores (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Catalogo de precios de referencia: lo que cada proveedor lista por un
-- producto, cargado siempre a mano. No influye en ninguna compra ni en
-- ningun calculo; es lo que se consulta al momento de comprar.
-- productos.id_proveedor sigue siendo el proveedor habitual: son dos datos
-- distintos. DECIMAL y no FLOAT porque la columna es nueva y no se suma con
-- ningun otro importe (ver ARCHITECTURE.md, deuda de float).
CREATE TABLE IF NOT EXISTS producto_proveedor (
    id                BINARY(16)    NOT NULL,
    id_producto       BINARY(16)    NOT NULL,
    id_proveedor      BINARY(16)    NOT NULL,
    precio_referencia DECIMAL(12,2) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    deleted_at        DATETIME(6)   NULL,
    -- Un solo precio activo por par, y borrar la referencia libera el par.
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

-- =====================================================================
-- 3. INVENTARIO: STOCK, LOTES, MOVIMIENTOS
-- =====================================================================

CREATE TABLE IF NOT EXISTS stock (
    id              BINARY(16)  NOT NULL,
    id_producto     BINARY(16)  NULL,
    cantidad        INT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    producto_activo BINARY(16)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, id_producto, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_producto_activo (producto_activo),
    KEY ix_stock_producto (id_producto, deleted_at),
    CONSTRAINT fk_stock_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS lote (
    id                  BINARY(16)   NOT NULL,
    id_producto         BINARY(16)   NULL,
    numero_lote         VARCHAR(255) NULL,
    estado              ENUM('PROXIMO','SIN_FECHA','VENCIDO','VIGENTE') NULL,
    fecha_vencimiento   DATE         NULL,
    cantidad            INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_lote_producto_vencimiento (id_producto, deleted_at, fecha_vencimiento),
    KEY ix_lote_vencimiento (fecha_vencimiento, deleted_at),
    CONSTRAINT fk_lote_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS movimientos_stock (
    id              BINARY(16)   NOT NULL,
    id_producto     BINARY(16)   NULL,
    id_lote         BINARY(16)   NULL,
    cantidad        INT          NOT NULL,
    tipo            ENUM('AJUSTE','COMPRA','MERMA','VENTA') NULL,
    motivo          VARCHAR(255) NULL,
    id_usuario      BINARY(16)   NULL,
    id_referencia   BINARY(16)   NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_mov_stock_producto_fecha (id_producto, deleted_at, created_at),
    KEY ix_mov_stock_lote (id_lote),
    KEY ix_mov_stock_usuario (id_usuario),
    KEY ix_mov_stock_referencia (id_referencia, tipo, deleted_at),
    CONSTRAINT fk_mov_stock_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_stock_lote
        FOREIGN KEY (id_lote) REFERENCES lote (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_stock_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- =====================================================================
-- 4. CAJA: SESIONES Y MOVIMIENTOS
-- =====================================================================

CREATE TABLE IF NOT EXISTS sesiones_caja (
    id                      BINARY(16)   NOT NULL,
    fecha_apertura          DATETIME(6)  NOT NULL,
    fecha_cierre            DATETIME(6)  NULL,
    saldo_inicial           FLOAT        NOT NULL,
    saldo_final             FLOAT        NULL,
    saldo_esperado          FLOAT        NULL,
    diferencia              FLOAT        NULL,
    -- Al cerrar, una parte del efectivo se retira y otra queda en la caja. El retiro
    -- se registra ademas como movimiento SALIDA con origen RETIRO, para que el dia no
    -- vuelva a sumar como apertura del turno siguiente la plata que nunca salio.
    monto_retirado          FLOAT        NULL,
    saldo_dejado            FLOAT        NULL,
    -- Cuanto se aparto lo contado al abrir de lo que dejo el cierre anterior.
    diferencia_apertura     FLOAT        NULL,
    total_ventas            FLOAT        NULL,
    cantidad_ventas         INT          NULL,
    total_compras           FLOAT        NULL,
    cantidad_compras        INT          NULL,
    total_entradas_manuales FLOAT        NULL,
    total_salidas_manuales  FLOAT        NULL,
    observaciones           VARCHAR(255) NULL,
    id_usuario_apertura     BINARY(16)   NOT NULL,
    id_usuario_cierre       BINARY(16)   NULL,
    estado                  ENUM('ABIERTA','CERRADA') NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    deleted_at              DATETIME(6)  NULL,
    sesion_abierta VARCHAR(10)
        GENERATED ALWAYS AS (IF(estado = 'ABIERTA' AND deleted_at IS NULL, 'ABIERTA', NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sesiones_una_abierta (sesion_abierta),
    KEY ix_sesiones_estado_fecha (estado, deleted_at, created_at),
    -- El historial de cortes ordena por fecha_cierre, que no es el mismo orden que
    -- created_at: un turno puede abrirse antes que otro y cerrarse despues.
    KEY ix_sesiones_estado_cierre (estado, deleted_at, fecha_cierre),
    KEY ix_sesiones_created_at (created_at),
    KEY ix_sesiones_usuario_apertura (id_usuario_apertura),
    KEY ix_sesiones_usuario_cierre (id_usuario_cierre),
    CONSTRAINT fk_sesiones_usuario_apertura
        FOREIGN KEY (id_usuario_apertura) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sesiones_usuario_cierre
        FOREIGN KEY (id_usuario_cierre) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS movimientos_caja (
    id              BINARY(16)  NOT NULL,
    id_sesion       BINARY(16)  NOT NULL,
    tipo            ENUM('ENTRADA','SALIDA') NOT NULL,
    monto           FLOAT       NOT NULL,
    motivo          VARCHAR(255) NULL,
    id_usuario      BINARY(16)  NOT NULL,
    origen          VARCHAR(20) NULL,
    id_referencia   BINARY(16)  NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_mov_caja_sesion (id_sesion, deleted_at),
    KEY ix_mov_caja_sesion_tipo_origen (id_sesion, tipo, origen, deleted_at),
    KEY ix_mov_caja_fecha (created_at, deleted_at),
    KEY ix_mov_caja_usuario (id_usuario),
    KEY ix_mov_caja_referencia (id_referencia),
    CONSTRAINT fk_mov_caja_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones_caja (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_caja_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_mov_caja_origen
        CHECK (origen IS NULL OR origen IN ('MANUAL','VENTA','COMPRA','REVERSA','RETIRO'))
) ENGINE = InnoDB;

-- =====================================================================
-- 5. VENTAS
-- =====================================================================

-- El id lo puede generar el backend o el front —un ticket creado sin conexion trae el
-- suyo—, y en los dos casos es un UUIDv7: sus 48 bits altos son el timestamp, asi que
-- como clustered index de InnoDB las inserciones quedan casi secuenciales. Un v4 aca
-- fragmenta las paginas, y por eso el endpoint de sync rechaza lo que no sea v7.
--
-- created_at es cuando ocurrio el ticket, no cuando se inserto la fila: en una venta
-- offline la manda el front y puede ser de hace dos dias. sincronizado_en es cuando
-- llego a MySQL, y queda NULL en las ventas online.
--
-- Los importes van en DECIMAL: el total lo suma tambien el front y las dos cuentas
-- tienen que dar igual, cosa que con FLOAT no pasa.
CREATE TABLE IF NOT EXISTS ventas (
    id                BINARY(16)    NOT NULL,
    id_usuario        BINARY(16)    NOT NULL,
    total             DECIMAL(12,2) NOT NULL,
    cobrada           BIT(1)        NULL DEFAULT b'0',
    fecha_cobro       DATETIME(6)   NULL,
    metodo_pago       VARCHAR(20)   NULL,
    monto_recibido    DECIMAL(12,2) NULL,
    id_sesion         BINARY(16)    NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    deleted_at        DATETIME(6)   NULL,
    sincronizado_en   DATETIME(6)   NULL,
    origen            VARCHAR(10)   NOT NULL DEFAULT 'ONLINE',
    dispositivo       VARCHAR(50)   NULL,
    id_usuario_sync   BINARY(16)    NULL,
    requiere_revision BIT(1)        NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY ix_ventas_fecha (created_at, deleted_at),
    KEY ix_ventas_usuario (id_usuario, deleted_at),
    KEY ix_ventas_cobrada_fecha (cobrada, deleted_at, created_at),
    KEY ix_ventas_fecha_cobro (fecha_cobro, deleted_at),
    KEY ix_ventas_sesion (id_sesion),
    KEY ix_ventas_revision (requiere_revision, deleted_at),
    CONSTRAINT fk_ventas_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ventas_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones_caja (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ventas_usuario_sync
        FOREIGN KEY (id_usuario_sync) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS detalles_ventas (
    id              BINARY(16)    NOT NULL,
    id_venta        BINARY(16)    NOT NULL,
    id_producto     BINARY(16)    NULL,
    nombre_producto VARCHAR(255)  NULL,
    cantidad        INT           NOT NULL,
    precio_unitario DECIMAL(12,2) NOT NULL,
    costo_unitario  DECIMAL(12,2) NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    deleted_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY ix_det_ventas_venta (id_venta, deleted_at),
    KEY ix_det_ventas_producto (id_producto, deleted_at),
    CONSTRAINT fk_det_ventas_venta
        FOREIGN KEY (id_venta) REFERENCES ventas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_det_ventas_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Una anulacion que llego antes que el CREAR de su propio ticket: el lote se corto entre
-- los dos eventos. La fila espera aca hasta que la venta llegue, y por eso no tiene FK a
-- ventas: la venta todavia no existe, que es toda la razon de ser de la tabla.
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

-- =====================================================================
-- 6. COMPRAS
-- =====================================================================

CREATE TABLE IF NOT EXISTS compras (
    id                  BINARY(16)   NOT NULL,
    id_usuario          BINARY(16)   NULL,
    total               FLOAT        NOT NULL,
    id_proveedor        BINARY(16)   NULL,
    tipo_comprobante    VARCHAR(20)  NULL,
    nro_comprobante     VARCHAR(50)  NULL,
    observaciones       VARCHAR(255) NULL,
    id_sesion           BINARY(16)   NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    -- Un proveedor no repite numero dentro del mismo tipo de comprobante: su
    -- factura 0001-00001234 y su remito 0001-00001234 son dos documentos, y dos
    -- proveedores distintos pueden coincidir sin problema. CONCAT devuelve NULL
    -- si falta el numero y el IF deja fuera a las anuladas; como los NULL no
    -- chocan entre si en un indice unico, las dos exenciones salen solas.
    comprobante_activo VARCHAR(72)
        GENERATED ALWAYS AS (
            IF(deleted_at IS NULL,
               CONCAT(IFNULL(tipo_comprobante, ''), '|', nro_comprobante),
               NULL)
        ) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compras_proveedor_comprobante (id_proveedor, comprobante_activo),
    KEY ix_compras_fecha (created_at, deleted_at),
    KEY ix_compras_usuario (id_usuario, deleted_at),
    KEY ix_compras_proveedor (id_proveedor, deleted_at),
    KEY ix_compras_sesion (id_sesion),
    CONSTRAINT fk_compras_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_compras_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_compras_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones_caja (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS detalles_compras (
    id                  BINARY(16)   NOT NULL,
    id_compra           BINARY(16)   NULL,
    id_producto         BINARY(16)   NULL,
    nombre_producto     VARCHAR(255) NULL,
    barcode             VARCHAR(255) NULL,
    precio_unitario     FLOAT        NOT NULL DEFAULT 0,
    cantidad            INT          NOT NULL DEFAULT 0,
    total               FLOAT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_det_compras_compra (id_compra, deleted_at),
    KEY ix_det_compras_producto (id_producto, deleted_at),
    KEY ix_det_compras_barcode (barcode, deleted_at),
    KEY ix_det_compras_nombre (nombre_producto, deleted_at),
    CONSTRAINT fk_det_compras_compra
        FOREIGN KEY (id_compra) REFERENCES compras (id) ON DELETE RESTRICT,
    CONSTRAINT fk_det_compras_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 7. DATOS INICIALES
-- =====================================================================
-- Lo minimo para que el sistema sea usable: las dos cuentas de acceso y
-- un catalogo chico para probar los dos caminos de stock (agregado y por
-- lote). Para un catalogo grande, correr despues seed_demo.sql.

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
