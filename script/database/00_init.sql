
-- Convenciones del dominio:
--   * Soft delete: columna `deleted_at` NULL = registro activo.
--   * Auditoría:   `created_at` / `updated_at` (@CreationTimestamp / @UpdateTimestamp).
--
-- mysql -u root -p < 00_init.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS minimarket
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE minimarket;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 1. USUARIOS Y AUTENTICACIÓN
-- =====================================================================

-- Usuarios del sistema. `email` es la credencial de login (único).
CREATE TABLE IF NOT EXISTS usuarios (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(50)  NOT NULL,
    apellido        VARCHAR(50)  NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL,
    hash_password   VARCHAR(255) NOT NULL,               -- BCrypt
    rol             ENUM('ADMIN','EMPLEADO') NOT NULL,
    enabled         BIT(1)       NOT NULL DEFAULT b'0',  -- se activa al verificar el email
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_email (email),
    KEY ix_usuarios_deleted_at (deleted_at)
) ENGINE = InnoDB;

-- Tokens de un solo uso: verificación de email y reseteo de contraseña.
CREATE TABLE IF NOT EXISTS auth_tokens (
    id              BINARY(16)  NOT NULL,
    token_type      ENUM('PASSWORD_RESET','VERIFICATION') NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,                -- SHA-256 del token en claro
    user_id         BINARY(16)  NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    used            BIT(1)      NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_tokens_token_hash (token_hash),
    KEY ix_auth_tokens_user_tipo_used (user_id, token_type, used),
    CONSTRAINT fk_auth_tokens_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Refresh tokens de sesión JWT.
CREATE TABLE IF NOT EXISTS refresh_token (
    id              BINARY(16)  NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    is_active       BIT(1)      NOT NULL DEFAULT b'1',
    revoked_at      DATETIME(6) NULL,
    expires_at      DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token_hash (token_hash),
    KEY ix_refresh_token_user_activo (user_id, is_active),
    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- =====================================================================
-- 2. CATÁLOGO: CATEGORÍAS, PROVEEDORES, PRODUCTOS
-- =====================================================================

CREATE TABLE IF NOT EXISTS categorias (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    descripcion     VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categorias_nombre (nombre),
    KEY ix_categorias_deleted_at (deleted_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS proveedores (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    telefono        VARCHAR(50)  NULL,
    email           VARCHAR(100) NULL,
    direccion       VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_proveedores_deleted_at (deleted_at),
    KEY ix_proveedores_nombre (nombre)
) ENGINE = InnoDB;

-- Catálogo de productos. El stock NO vive acá: está en `stock` (total) y
-- en `lote` (por lote) según el flag maneja_lotes.
CREATE TABLE IF NOT EXISTS productos (
    id              BINARY(16)   NOT NULL,
    nombre          VARCHAR(255) NULL,
    barcode         VARCHAR(255) NULL,
    precio          FLOAT        NOT NULL,               -- precio de venta
    costo           FLOAT        NULL,                   -- costo de compra
    margen          FLOAT        NULL,                   -- margen sobre el costo
    maneja_lotes    BIT(1)       NOT NULL DEFAULT b'0',  -- true => descuento FIFO por lote
    id_categoria    BINARY(16)   NULL,
    id_proveedor    BINARY(16)   NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_productos_barcode (barcode),
    KEY ix_productos_categoria (id_categoria, deleted_at),
    KEY ix_productos_proveedor (id_proveedor, deleted_at),
    KEY ix_productos_deleted_at (deleted_at),
    CONSTRAINT fk_productos_categoria
        FOREIGN KEY (id_categoria) REFERENCES categorias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_productos_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES proveedores (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- =====================================================================
-- 3. INVENTARIO: STOCK, LOTES, MOVIMIENTOS
-- =====================================================================

-- Stock agregado por producto (una fila activa por producto).
CREATE TABLE IF NOT EXISTS stock (
    id              BINARY(16)  NOT NULL,
    id_producto     BINARY(16)  NULL,
    cantidad        INT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_stock_producto (id_producto, deleted_at),
    CONSTRAINT fk_stock_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Lotes con vencimiento (solo productos con maneja_lotes = 1).
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
    -- Soporta el consumo FIFO: lotes del producto ordenados por vencimiento.
    KEY ix_lote_producto_vencimiento (id_producto, deleted_at, fecha_vencimiento),
    KEY ix_lote_vencimiento (fecha_vencimiento, deleted_at),
    CONSTRAINT fk_lote_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS movimientos_stock (
    id              BINARY(16)   NOT NULL,
    id_producto     BINARY(16)   NULL,
    id_lote         BINARY(16)   NULL,                   -- NULL si el producto no maneja lotes
    cantidad        INT          NOT NULL,
    tipo            ENUM('AJUSTE','COMPRA','MERMA','VENTA') NULL,
    motivo          VARCHAR(255) NULL,
    id_usuario      BINARY(16)   NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_mov_stock_producto_fecha (id_producto, deleted_at, created_at),
    KEY ix_mov_stock_lote (id_lote),
    KEY ix_mov_stock_usuario (id_usuario),
    CONSTRAINT fk_mov_stock_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_stock_lote
        FOREIGN KEY (id_lote) REFERENCES lote (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_stock_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- =====================================================================
-- 4. CAJA
-- =====================================================================

-- Turno de caja: se abre con un saldo inicial y se cierra con un arqueo.
CREATE TABLE IF NOT EXISTS sesiones_caja (
    id                      BINARY(16)   NOT NULL,
    fecha_apertura          DATETIME(6)  NOT NULL,
    fecha_cierre            DATETIME(6)  NULL,
    saldo_inicial           FLOAT        NOT NULL,
    saldo_final             FLOAT        NULL,           -- contado físicamente al cierre
    saldo_esperado          FLOAT        NULL,           -- calculado por el sistema
    diferencia              FLOAT        NULL,           -- saldo_final - saldo_esperado
    observaciones           VARCHAR(255) NULL,
    id_usuario_apertura     BINARY(16)   NOT NULL,
    id_usuario_cierre       BINARY(16)   NULL,
    estado                  ENUM('ABIERTA','CERRADA') NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    deleted_at              DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- Soporta "buscar la última sesión abierta".
    KEY ix_sesiones_estado_fecha (estado, deleted_at, created_at),
    KEY ix_sesiones_created_at (created_at),
    KEY ix_sesiones_usuario_apertura (id_usuario_apertura),
    KEY ix_sesiones_usuario_cierre (id_usuario_cierre),
    CONSTRAINT fk_sesiones_usuario_apertura
        FOREIGN KEY (id_usuario_apertura) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sesiones_usuario_cierre
        FOREIGN KEY (id_usuario_cierre) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Movimientos de efectivo de una sesión.
-- origen: 'MANUAL' (ingreso/retiro a mano), 'VENTA' o 'COMPRA' (automáticos).
-- id_referencia apunta a ventas.id o compras.id según el origen: al ser
-- polimórfica NO lleva FK.
CREATE TABLE IF NOT EXISTS movimientos_caja (
    id              BINARY(16)   NOT NULL,
    id_sesion       BINARY(16)   NOT NULL,
    tipo            ENUM('ENTRADA','SALIDA') NOT NULL,
    monto           FLOAT        NOT NULL,
    motivo          VARCHAR(255) NULL,
    id_usuario      BINARY(16)   NOT NULL,
    origen          VARCHAR(20)  NULL,
    id_referencia   BINARY(16)   NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
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
        CHECK (origen IS NULL OR origen IN ('MANUAL','VENTA','COMPRA'))
) ENGINE = InnoDB;

-- =====================================================================
-- 5. VENTAS
-- =====================================================================

-- La venta se crea sin cobrar (cobrada = 0) y se cobra en un segundo paso,
-- momento en el que se asocia a la sesión de caja y se registra el ingreso.
CREATE TABLE IF NOT EXISTS ventas (
    id              BINARY(16)  NOT NULL,
    id_usuario      BINARY(16)  NOT NULL,
    total           FLOAT       NOT NULL,
    cobrada         BIT(1)      NULL DEFAULT b'0',
    fecha_cobro     DATETIME(6) NULL,
    metodo_pago     VARCHAR(20) NULL,                    -- EFECTIVO / TARJETA / ...
    monto_recibido  FLOAT       NULL,
    id_sesion       BINARY(16)  NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_ventas_fecha (created_at, deleted_at),
    KEY ix_ventas_usuario (id_usuario, deleted_at),
    KEY ix_ventas_cobrada_fecha (cobrada, deleted_at, created_at),
    KEY ix_ventas_sesion (id_sesion),
    CONSTRAINT fk_ventas_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ventas_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones_caja (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- Líneas de venta. id_producto es NULL para ítems MANUAL (venta suelta sin
-- producto de catálogo): en ese caso solo se guarda nombre_producto.
-- nombre_producto y precio_unitario se copian al momento de la venta para
-- que el histórico no cambie si luego se edita el producto.
CREATE TABLE IF NOT EXISTS detalles_ventas (
    id              BINARY(16)   NOT NULL,
    id_venta        BINARY(16)   NOT NULL,
    id_producto     BINARY(16)   NULL,
    nombre_producto VARCHAR(255) NULL,
    cantidad        INT          NOT NULL,
    precio_unitario FLOAT        NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_det_ventas_venta (id_venta, deleted_at),
    KEY ix_det_ventas_producto (id_producto, deleted_at),
    CONSTRAINT fk_det_ventas_venta
        FOREIGN KEY (id_venta) REFERENCES ventas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_det_ventas_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- =====================================================================
-- 6. COMPRAS
-- =====================================================================

CREATE TABLE IF NOT EXISTS compras (
    id                  BINARY(16)   NOT NULL,
    id_usuario          BINARY(16)   NULL,
    total               FLOAT        NOT NULL,
    id_proveedor        BINARY(16)   NULL,
    tipo_comprobante    VARCHAR(20)  NULL,               -- FACTURA / REMITO / TICKET / ...
    nro_comprobante     VARCHAR(50)  NULL,
    observaciones       VARCHAR(255) NULL,
    id_sesion           BINARY(16)   NULL,               -- si se pagó desde la caja
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
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

-- Líneas de compra. nombre_producto y barcode se copian como snapshot.
CREATE TABLE IF NOT EXISTS detalles_compras (
    id              BINARY(16)   NOT NULL,
    id_compra       BINARY(16)   NULL,
    id_producto     BINARY(16)   NULL,
    nombre_producto VARCHAR(255) NULL,
    barcode         VARCHAR(255) NULL,
    precio_unitario FLOAT        NOT NULL DEFAULT 0,     -- costo unitario de compra
    cantidad        INT          NOT NULL DEFAULT 0,
    total           FLOAT        NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
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