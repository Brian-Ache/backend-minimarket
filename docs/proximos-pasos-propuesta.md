# Próximos pasos — Propuesta

> Ideas y funcionalidades identificadas como relevantes para el sistema.

---

## 1. Facturación electrónica (ARCA)

**Contexto:** Conforme a la normativa argentina vigente, los comprobantes electrónicos deben emitirse a través de los servicios web de ARCA (ex AFIP). Esto aplica a facturas A, B, C, notas de crédito/débito, y remitos electrónicos.

**Consideraciones:**

- Se necesita un módulo que integre con los WS de ARCA (`wsfe`, `wsfev1`, etc.)
- Manejo de puntos de venta (PV) y numeración secuencial por PV
- Almacenamiento de CAE / CAEA en cada comprobante emitido
- Posibilidad de emitir comprobantes electrónicos en diferido (offline → online)
- Relación con módulos existentes: al `POST /ventas/v1` y `POST /compras/v1` se asociaría un comprobante electrónico

**Posible enfoque:**

- Nuevo módulo `facturacion/` con entidad `ComprobanteElectronico` (tipo, letra, pv, numero, cae, vencimientoCAE, xmlRequest, xmlResponse)
- Integración vía `FacturacionApi` similar al patrón `CajaApi` usado para movimientos automáticos
- Configuración por sucursal/empresa: CUIT, puntos de venta, certificados digitales
- Servicio agendado (`@Scheduled`) para re-intentar comprobantes pendientes

**Prioridad sugerida:** Media-alta (depende de si el cliente emite factura electrónica o solo tickets/POS)

---

## 2. Promociones, descuentos y ofertas

**Contexto:** El sistema actual maneja precios fijos por producto. Para soportar estrategias comerciales se necesita un módulo de reglas promocionales.

**Casos de uso:**

- Descuento por porcentaje sobre un producto (ej: "10% off en todos los lácteos")
- Descuento por monto fijo (ej: "$200 de descuento en compra > $5000")
- 2x1 o llevar X pagar Y
- Promociones por medio de pago (ej: "10% extra con transferencia")
- Vigencia con fecha de inicio y fin

**Posible enfoque:**

- Nuevo módulo `promociones/` con entidad `Promocion` (tipo, valor, condiciones, fechas de vigencia, productos/categorías asociadas)
- Al crear una venta, el servicio de promociones evalúa reglas aplicables y ajusta `precioUnitario` en los detalles o agrega un ítem descuento
- Tabla pivote `promocion_producto` y `promocion_categoria`

**Prioridad sugerida:** Media (diferible a una segunda etapa del producto)

---

## 3. Sesiones por usuario vs. por dispositivo

**Contexto:** Hoy `SesionCaja` se asocia a un usuario a través de `idUsuarioApertura`, y solo puede haber una sesión activa a la vez. Queda abierta la decisión de si esto debe ser por dispositivo físico (caja registradora) o por usuario.

**Consideraciones:**

- **Modelo actual:** Una sesión activa global → adecuado para un único punto de venta
- **Modelo por usuario:** Cada empleado inicia su propia sesión; útil en locales con múltiples cajeros que rinden por separado
- **Modelo por dispositivo:** Una sesión por terminal; varios empleados pueden operar la misma caja en distintos turnos
- Impacta en movimientos de caja, cobro de ventas y resumen diario

**Posible enfoque:**

- Agregar `idSucursal` a `SesionCaja` para soportar múltiples sucursales
- Decidir en configuración si la validación de "sesión única activa" es por usuario o global
- Extender `GET /caja/v1/sesion-activa` con filtro opcional por usuario o sucursal

**Prioridad sugerida:** Alta si el cliente opera con más de un punto de venta simultáneo; baja si es un único local con una sola caja

---

## 4. Notificaciones de stock bajo y vencimientos

**Contexto:** El módulo de inventario y lotes ya registra stock y vencimientos. Falta un mecanismo que alerte cuando un producto está por debajo de un umbral mínimo o cuando un lote está próximo a vencer.

**Casos de uso:**

- Alerta al abrir la caja: "X productos con stock bajo"
- Notificación diaria: "Y lotes vencen en los próximos 7 días"
- Umbral configurable por producto (stock mínimo)
- Posibilidad de enviar notificaciones in-app (no email/SMS en primera instancia)

**Posible enfoque:**

- Agregar campo `stockMinimo` (int, default 0) a la entidad `Producto`
- Servicio agendado (`@Scheduled` diario) que genera una tabla `notificaciones` (tipo, mensaje, leída, fecha)
- Endpoint `GET /api/notificaciones/v1` para que el frontend las consuma
- Al abrir sesión de caja, devolver alertas de stock bajo como parte de la respuesta

**Prioridad sugerida:** Media-alta (agrega valor operativo sin ser bloqueante)
