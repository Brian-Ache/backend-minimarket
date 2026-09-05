# Roadmap

> Lo que falta, en dos partes: la **deuda técnica** que ya duele y las **funcionalidades**
> identificadas como relevantes pero todavía no decididas. Lo que ya está hecho no vive acá: está
> en el [`CHANGELOG`](../CHANGELOG.md), y el porqué de cómo quedó, en
> [`ARCHITECTURE.md`](../ARCHITECTURE.md).

---

## Deuda técnica

Ordenada por cuánto cuesta convivir con ella.

### 1. Los importes son `float`

**Dónde:** precios, costos, totales de venta y compra, y todos los saldos de caja.

Para dinero corresponde `DECIMAL` + `BigDecimal`. Mientras siga así, cada suma acumula error de
redondeo: un arqueo con muchos movimientos puede cerrar con una diferencia de centavos que no
corresponde a ningún faltante real.

`producto_proveedor.precio_referencia` (0.5.0) ya nació en `DECIMAL`, porque es una columna nueva
que no se suma con ninguna otra. Migrar el resto es un trabajo aparte y grande: toca todas las
entidades, todos los DTOs, el esquema y el histórico ya cargado.

**Prioridad:** alta. Es el único punto de esta lista que produce datos incorrectos.

### 2. Falta Flyway

Once migraciones aplicadas a mano, en orden y con la aplicación detenida. `00_init_limpio.sql` se
mantiene en paralelo para las instalaciones nuevas, así que cada cambio de esquema se escribe dos
veces y nada garantiza que no se desincronicen.

Convertir `00_init_limpio.sql` en `V1__init.sql` y las migraciones `04`–`11` en `V2__…`–`V9__…`.

**Prioridad:** alta. Cuanto más se demore, más scripts hay que ordenar.

### 3. No hay tests de integración

Los 258 tests son unitarios con mocks: verifican la lógica de los servicios, no que las consultas
JPQL y los índices hagan lo que se espera contra una base real.
`MinimarketApplicationTests.contextLoads` es el único que necesita MySQL levantado, y solo prueba
que el contexto arranque.

Las consultas que hoy no cubre ningún test contra base: los lotes ajustables, la última compra
por proveedor, las agregadas de existencias y los `Page` de cada listado —donde lo que importa es
justamente que el orden y el desempate se comporten en la base como se espera—.

Con Testcontainers se resuelve sin depender de una base local.

**Prioridad:** media-alta.

### 4. `lote.estado` es dato muerto

Se escribe al crear el lote y no se refresca nunca. Lo que la API devuelve es el estado calculado
al leer, contra el día de hoy, que es la única forma correcta: un lote pasa de `VIGENTE` a
`VENCIDO` sin que nadie lo toque.

La columna sobrevive sin usarse y puede confundir a quien lea la tabla a mano. Sacarla es una
migración de una línea; conviene hacerla junto con otra.

**Prioridad:** baja.

### 5. El access token no se puede revocar antes de que expire

El logout invalida el refresh token, pero el JWT ya emitido sigue valiendo hasta su vencimiento.
Con 24 h por defecto la ventana es amplia; conviene bajar `JWT_EXPIRATION_HOURS` a 1–2 h y
apoyarse en el refresh.

La baja, el bloqueo y el cambio de rol **sí** tienen efecto inmediato, porque el filtro consulta
la base en cada request.

**Prioridad:** baja, y se mitiga con configuración.

---

## Funcionalidades propuestas

Ninguna está decidida. Se listan con el enfoque que hoy parece razonable, para no volver a
pensarlo desde cero.

### Facturación electrónica (ARCA)

Los comprobantes electrónicos deben emitirse por los servicios web de ARCA (ex AFIP): facturas A,
B y C, notas de crédito y débito, y remitos.

- Módulo `facturacion/` con una entidad `ComprobanteElectronico` (tipo, letra, punto de venta,
  número, CAE, vencimiento del CAE, XML de request y de response).
- Numeración secuencial **por punto de venta**, y configuración por empresa: CUIT, puntos de
  venta y certificados digitales.
- Se integraría por `FacturacionApi`, con el mismo patrón de contrato que usa `CajaApi` para los
  movimientos automáticos: al cobrar una venta o registrar una compra se le asocia el
  comprobante.
- Emisión diferida —offline primero, online después— con un `@Scheduled` que reintente los
  pendientes.

**Depende de** si el comercio emite factura electrónica o solo tickets. Es lo primero que hay que
averiguar: cambia la prioridad de media-alta a nula.

### Promociones y descuentos

Hoy el precio de un producto es fijo. Para estrategias comerciales haría falta un motor de
reglas: porcentaje sobre un producto o categoría, monto fijo sobre un total, 2x1 o llevar X pagar
Y, descuento por medio de pago, todo con fecha de vigencia.

- Módulo `promociones/` con `Promocion` (tipo, valor, condiciones, vigencia) y tablas pivote
  `promocion_producto` y `promocion_categoria`.
- Al crear la venta, el servicio evalúa las reglas aplicables y ajusta el `precioUnitario` de los
  detalles o agrega una línea de descuento.

Ojo con el snapshot: el precio y el costo se congelan al vender, así que el descuento aplicado
tiene que quedar guardado en el detalle y no recalcularse después, o la ganancia histórica se
reescribe sola.

**Prioridad:** media. Diferible a una segunda etapa.

### Alertas de stock bajo y vencimientos

El inventario ya registra existencias y vencimientos, y `EstadoLote` ya sabe qué lote está
próximo a vencer. Falta que alguien avise sin que haya que ir a mirar.

- `stockMinimo` (int, 0 por defecto) en `Producto`.
- Un `@Scheduled` diario que escriba una tabla `notificaciones` (tipo, mensaje, leída, fecha) y
  un `GET /api/notificaciones/v1` para consumirlas.
- Al abrir la caja, devolver las alertas como parte de la respuesta: es el momento del día en que
  alguien las va a leer.

In-app, no mail ni SMS en una primera etapa.

**Prioridad:** media-alta. Agrega valor operativo sin bloquear nada.

### Varias cajas o varias sucursales

Hoy hay **una sola sesión de caja abierta a la vez**, garantizada por índice único, y se asocia
al usuario que la abrió. Alcanza para un local con una caja.

Tres modelos posibles, y la decisión no es técnica:

| Modelo | Cuándo conviene |
|---|---|
| Una sesión global (actual) | Un único punto de venta |
| Una por usuario | Varios cajeros que rinden por separado |
| Una por dispositivo | Una terminal con varios empleados por turno |

Si se avanza, `SesionCaja` necesitaría `idSucursal`, la validación de sesión única pasaría a ser
configurable, y `GET /caja/v1/sesion-activa` tendría que filtrar. Es un cambio que toca el cobro
de ventas, los movimientos de caja y el resumen diario.

**Prioridad:** alta si el comercio opera más de un punto de venta simultáneo; baja si no.
