# Plan de implementación 0.6.0 — sincronización offline-first y despliegue

> Sale de dos lugares: el documento de requisitos [`requisitos-sync-offline-tickets.md`](../requisitos-sync-offline-tickets%20(1).md)
> —tickets creados en el front, enviados por lote, anulables offline— y los dos pendientes
> anotados en [`ideas.md`](ideas.md) —imagen Docker y despliegue automático al VPS—.
>
> Son **dos tracks independientes**: el A toca el corazón del módulo de ventas, el B no toca
> código de negocio. No se bloquean entre sí y pueden avanzar en paralelo.
>
> Lo que este documento **no** es: una especificación del front. Acá está solo lo que el backend
> tiene que hacer, más los contratos que el front necesita para trabajar contra él.

---

## 1. El terreno: dónde choca lo pedido con lo que ya hay

El backend actual no es neutral respecto de estos requisitos. Diez puntos de fricción concretos,
que son los que definen el tamaño real del trabajo:

| # | Lo que pide el requisito | Lo que hace hoy el código | Dónde |
|---|---|---|---|
| 1 | El `uuid` lo genera el front (RF-02, RF-17) | El id lo genera Hibernate al persistir | [Venta.java](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/entity/Venta.java) — `@GeneratedValue(strategy = UUID)` |
| 2 | PK `BIGINT AUTO_INCREMENT` + `uuid` como `UNIQUE KEY` (RNF-05) | PK `BINARY(16)` en las 16 tablas, con tres referencias apuntando a `ventas.id` | `00_init_limpio.sql:284-306` |
| 3 | Un endpoint que recibe un array y responde por ítem (RF-06, RF-08) | `POST /api/ventas/v1` recibe una venta y responde una | [VentaController.java](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/controller/VentaController.java) |
| 4 | El ticket llega **ya cobrado**, de hace días | La venta nace `cobrada=false`; el cobro es un segundo paso que resuelve la **sesión de caja activa** | [VentaService.java:425-429](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L425-L429) |
| 5 | Se puede anular un ticket ya sincronizado (RF-11) | Una venta cobrada **no se anula**: "Registrá una devolución" | [VentaService.java:262-265](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L262-L265) |
| 6 | Venta offline sin validar stock; el backend resuelve después (RF-24, RF-25) | Falta stock ⇒ `400` y la venta entera se aborta | [VentaService.java:140](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L140) |
| 7 | El vendedor del ticket es un dato histórico que viaja en el payload | Regla de arquitectura: "ningún endpoint acepta el id de usuario del cliente" | `ARCHITECTURE.md`, sección Seguridad |
| 8 | El vendedor anula desde su pantalla (RF-11, ventana de 7 días por D5) | Anular exige rol ADMIN | [SecurityConfig.java:76](../src/main/java/com/SolucionesInformaticasBA/minimarket/security/SecurityConfig.java#L76) |
| 9 | El ticket llega con su fecha de hace dos días (D8) | `@CreationTimestamp` pisa la fecha en cada `INSERT` | `Venta.java`, `DetalleVenta.java` |
| 10 | El turno de caja puede haber abierto sin conexión (D2) | La apertura y el corte los hace el backend con su propio reloj y su propio id | [CajaService.java:43-59](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/caja/service/CajaService.java#L43-L59) |

Dos cosas que **no** chocan, y conviene saberlo antes de tocarlas:

- **El barrido de reservas** ([`ReservaStockScheduler`](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/scheduler/ReservaStockScheduler.java))
  filtra por `cobrada = false`. Si los tickets offline se persisten cobrados —como corresponde—,
  el barrido no los ve y no hay nada que ajustar ahí. Si por algún camino entrara uno sin cobrar,
  a los 120 minutos el job lo anularía solo; es la única razón por la que el punto importa.
- **`movimientos_stock` como fuente de verdad.** La reversa por anulación ya trabaja sobre los
  movimientos y no sobre los detalles, así que repone cada lote en la cantidad exacta que se le
  sacó. Esa maquinaria sirve tal cual para el evento `ANULAR`: no hay que escribir nada nuevo.

---

## 2. Decisiones de diseño — las nueve cerradas

Cada una con su fundamento, porque es lo que hay que poder reconstruir en seis meses. **Las nueve
se cerraron el 2026-09-12**: no queda nada por decidir antes de escribir código.

### D1 · CERRADA. Un solo tipo de uuid en todo el sistema: **UUIDv7**

Todo identificador que genere el front es **UUIDv7**, sin excepciones: el del ticket, el de la
sesión de caja (D2) y el de cualquier entidad que el front origine más adelante. Y el backend hace
lo mismo en las tablas que reciben ids del front, para que `ventas` y `detalles_ventas` tengan un
único patrón de inserción venga el id de donde venga.

**Se descarta el `BIGINT AUTO_INCREMENT` de RNF-05.** El `uuid` del front se guarda en `ventas.id`,
la columna `BINARY(16)` que ya es PK.

El problema que RNF-05 quiere evitar es real —un UUID aleatorio como clustered index fragmenta las
páginas de InnoDB—, pero la solución del documento asume `CHAR(36)`, que no es lo que hay. Con
UUIDv7 los bits altos son el timestamp, así que las inserciones vuelven a ser casi secuenciales y
la fragmentación desaparece sin agregar una segunda identidad. A favor:

- `BINARY(16)` ocupa 16 bytes contra los 36 de `CHAR(36)`: menos de la mitad en cada índice
  secundario, que en InnoDB llevan la PK copiada.
- No se toca ninguna de las tres referencias a `ventas.id`: `detalles_ventas.id_venta` con FK, y
  las polimórficas `movimientos_stock.id_referencia` y `movimientos_caja.id_referencia`.
- No se rompe la convención de PK `UUID` de las 16 tablas ni aparecen dos identificadores del
  mismo ticket circulando por la API.

El costo de la alternativa del documento —columna nueva, unique key, reescribir las FKs, dos ids
en cada DTO, y una migración sobre la tabla que más crece— no se justifica para resolver algo que
el formato del uuid ya resuelve.

**Cómo se implementa la unificación**

| Dónde | Qué se hace |
|---|---|
| Front | Genera v7 para el ticket y para la sesión de caja. Es una línea de librería en cualquier stack; hay que fijarlo por escrito antes de que empiece a codear |
| Backend, al recibir | `UUID.version() == 7` o el evento responde `ERROR`. Vale para el uuid del ticket y para el de la sesión: un v4 colado fragmenta el índice igual |
| Backend, al generar | `Venta` y `DetalleVenta` dejan de usar `@GeneratedValue(strategy = UUID)` —que produce v4— y pasan por un helper `shared/Uuid7.nuevo()`: 48 bits de epoch en milisegundos, el nibble de versión y el resto aleatorio. Son unas quince líneas y se testea con dos llamadas seguidas comparando el orden |

Antes de escribir el helper conviene verificar `@UuidGenerator` de Hibernate 6.6, que ya trae una
estrategia v7; si funciona como se espera, se usa esa y no se escribe nada. El helper propio es el
plan B, no la primera opción.

**Alcance de la unificación:** solo `ventas` y `detalles_ventas`. Las otras catorce tablas siguen
con v4 hasta que haya un motivo —no lo hay: ninguna recibe ids del front ni crece al ritmo de las
ventas—. Unificar por unificar sería una migración de datos sin beneficio.

**Dos cosas a tener en cuenta.** El timestamp de un v7 sale del reloj del dispositivo: si está
mal, los ids dejan de ser monótonos y la fragmentación vuelve en esa proporción, pero nada se
rompe. Y ese timestamp **no es dato de negocio**: la fecha del ticket es la columna, nunca los
bits del uuid (ver D8).

### D2 · CERRADA. El ticket declara su sesión de caja, y la sesión también se sincroniza

El ticket lleva su `idSesion` en las tablas del front y lo manda en el evento. Eso permite
persistir `ventas.id_sesion` —la columna ya existe, con FK a `sesiones_caja`— y que el arqueo
cubra también lo cobrado sin internet, en vez de dejarlo afuera para conciliar a mano.

Hay una condición para que eso funcione: **la sesión tiene que existir en MySQL**. Y ahí está el
problema, porque hoy la apertura del turno la hace el backend
([`CajaService.abrirSesion`](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/caja/service/CajaService.java))
y un corte prolongado pisa un cambio de turno. Son dos escenarios distintos:

| Escenario | Qué pasa hoy | Qué hace falta |
|---|---|---|
| El turno abrió **online** y el corte empezó después | La sesión existe y sigue abierta —no se puede cerrar sin conexión—, así que el ticket puede referenciarla | Nada: solo aceptar el `idSesion` del evento en vez de resolverlo con `getIdSesionActiva()` |
| El turno abrió **durante el corte** | La sesión no existe en MySQL: el ticket rompe la FK `fk_ventas_sesion` | La apertura y el corte tienen que viajar como eventos del mismo lote |

**Decisión: la sesión de caja es una entidad más que el front origina y sincroniza.** El front
genera su uuid v7, y `ABRIR_SESION` y `CERRAR_SESION` se suman a los tipos de evento de la cola.
Como los eventos se procesan en orden local (RF-09), el lote llega ordenado solo:
`ABRIR_SESION` → los tickets del turno → `CERRAR_SESION`. Cuando el backend procesa el corte, las
ventas del turno ya están persistidas, así que calcula el saldo esperado con los datos completos y
el conteo físico que el cajero cargó en el local.

**Lo que sigue sin poder pasar** —y es la invariante que hay que preservar—: imputar un movimiento
a un turno ya cerrado. `registrarEntradaAutomatica` exige la sesión abierta, y eso no se toca. Un
ticket que llega después del `CERRAR_SESION` de su propio turno responde `ERROR`, no se cuela en
el corte firmado: el front lo marca para revisión y el admin lo resuelve. En la práctica no debería
pasar, porque los eventos van en orden.

**Límite conocido:** el índice `uk_sesiones_una_abierta` garantiza **una sola sesión abierta en
todo el sistema**. Alcanza para un local con una caja, que es el caso de hoy. Con dos cajas
operando offline en simultáneo (RNF-08), la apertura de la segunda choca contra ese índice al
sincronizar; pasar el índice a "una abierta por caja" es la decisión de *varias cajas o
sucursales* del roadmap y no se resuelve en esta versión.

### D3 · CERRADA. Faltante de stock: la venta se registra completa y el stock se regulariza

**El principio del que sale todo lo demás: esto es una caja registradora, no un ecommerce.** La
venta es física, ya ocurrió sobre el mostrador, y el vendedor no pudo haber entregado mercadería
que no existía. Si el sistema decía 4 y se vendieron 10, entonces **el sistema estaba mal**: las 10
unidades estaban en la góndola. La venta no produjo el faltante, lo reveló.

De ahí que no se descuente "hasta donde alcance". Con 4 en stock y un ticket de 10, en la misma
transacción y en este orden:

| Orden | Movimiento | Stock |
|---|---|---|
| 1 | `AJUSTE +6` — *Regularización por venta offline `<uuid>`* | 4 → 10 |
| 2 | `VENTA -10` — el ticket, completo | 10 → 0 |

Registrar la venta parcial y el faltante como un ajuste negativo —que era la propuesta anterior de
este documento— está mal por dos motivos concretos:

- **El kardex dejaría de sumar al stock**: movimientos por −10 contra una existencia que bajó 4.
  Hoy esa identidad se cumple y es lo que hace auditable el inventario.
- **La anulación repondría 4 en vez de 10.** `revertirStock` trabaja sobre los movimientos de tipo
  `VENTA` ([VentaService.java:317](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L317));
  si el cliente vuelve con las 10 unidades, las 10 entran al local. Con la venta registrada
  completa, la reversa da +10 sola y no hay que tocar una línea de esa maquinaria.

**Nunca stock negativo.** No le debemos unidades a nadie: el conteo estaba mal y quedó corregido.

**Detalles de implementación**

- El tipo del movimiento de regularización es `AJUSTE`: nadie clasifica por `tipo` fuera del módulo
  de inventario, así que no hace falta agregar un valor al ENUM de `movimientos_stock`
  (`00_init_limpio.sql:193`) ni la migración que eso implicaría. La superficie para encontrar estos
  casos es el flag `requiere_revision`, no el tipo del movimiento.
- En productos con lotes, las unidades regularizadas se cargan al lote que el FEFO estaba
  consumiendo. Si el producto no tiene ningún lote, se crea uno con `fecha_vencimiento` nula, que
  el modelo ya contempla (`SIN_FECHA`).
- Lo que el admin tiene que hacer después no es revisar el ticket —el ticket es válido— sino
  **contar ese producto**, con el ajuste contra conteo físico que ya existe (`/controlar`,
  `/lotes/ajustar`). El listado de revisión es el punto de entrada a ese trabajo.

### D4 · CERRADA. El vendedor viaja en el payload, y no se exige que esté activo

El evento `CREAR` trae `idVendedor`, porque el vendedor del ticket es un hecho de hace dos días y
no quien está sincronizando. Es una excepción a la regla "la identidad siempre sale del JWT", y hay
que escribirla en `ARCHITECTURE.md` con su motivo, no dejarla como una inconsistencia muda.

**Se valida que el usuario exista, nada más.** Exigir que esté activo rompe un caso real: si a un
empleado lo dan de baja o lo bloquean durante el corte, sus tickets pendientes quedarían rechazados
para siempre, reintentándose en cada ciclo sin poder entrar nunca. La mercadería salió y la plata
está en la caja; la baja es un hecho posterior a la venta y no la invalida.

**Esto amplía la confianza sobre el EMPLEADO**, que hoy solo puede crear ventas a su propio nombre:
por el endpoint de sync podría atribuir un ticket a un compañero. Se evaluó restringirlo a "un
EMPLEADO solo sincroniza eventos propios" y se descartó: con una terminal y cambio de turno, el que
está logueado cuando vuelve internet no es el vendedor de todos los tickets encolados, así que la
restricción bloquearía sincronizaciones legítimas. La mitigación es auditoría, no permiso:
`id_usuario_sync` —el del JWT— más `origen = OFFLINE` dejan el par vendedor/sincronizador a la
vista, y el vendedor ve en su propio listado una venta que no hizo. La solución de fondo son
credenciales por dispositivo en vez de por persona, y va al roadmap.

### D5 · CERRADA. Un EMPLEADO anula su propia venta, hasta 7 días

La regla queda así, y **vale igual por el endpoint de sync que por el panel**: no puede haber una
puerta más permisiva que la otra, o anular offline se vuelve la forma de saltear el permiso.

| Quién | Qué puede anular |
|---|---|
| EMPLEADO | Solo ventas **propias** (`venta.idUsuario` = quien anula), y solo si la sesión de caja de esa venta abrió hace **7 días o menos** |
| ADMIN / SUPERADMIN | Cualquier venta, sin ventana |

Tres consecuencias en el código:

1. `SecurityConfig.java:76` deja de exigir ADMIN para `DELETE /api/ventas/**` —`/api/compras/**`
   sigue como está— y la regla real se decide en `VentaService.delete`, que es donde se conocen el
   dueño y la sesión. Es el mismo criterio que ya usa el repo para los permisos que dependen del
   objetivo (ver `UsuarioService` en `ARCHITECTURE.md`).
2. **La venta cobrada pasa a ser anulable**, que hoy está prohibido de forma explícita
   ([VentaService.java:262-265](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L262-L265)).
   Era coherente cuando no existía la ventana; con 7 días, el 100% de lo anulable está cobrado.
3. La ventana se mide sobre `sesiones_caja.fecha_apertura` de la venta, no sobre su fecha: es el
   turno el que define el período contable. Una venta sin sesión —las que no son en efectivo
   cuando no había turno abierto— cae de vuelta en su propia fecha.

**Ojo con la reversa de caja.** Si la venta fue en efectivo, anularla devuelve plata. `compras`
resuelve el caso negándose cuando el turno cerró
([CompraService.java:328-350](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/compras/service/CompraService.java#L328-L350)),
pero acá esa regla vaciaría la ventana de 7 días: al segundo día ya no habría nada anulable.
**Decisión: el movimiento `REVERSA` va al turno abierto de hoy**, que es de donde físicamente sale
la plata cuando el cliente vuelve con el ticket. El corte viejo no se toca —queda como se firmó— y
el de hoy cuadra con el efectivo real. Si no hay ningún turno abierto, la anulación se rechaza con
"abrí la caja para devolver la plata": es la misma invariante de siempre, nunca imputar a un turno
cerrado. `OrigenMovimientoCaja.REVERSA` ya existe para esto.

**Impacto en los requisitos:** RF-11 y RF-16 hablan de anular dentro de los 47 días del historial
local. Con esta decisión la ventana del vendedor es de **7 días**; los 47 días siguen siendo la
ventana de **consulta** del historial. Hay que corregirlo en el documento de requisitos.

### D6 · CERRADA. Tope de 100 eventos por lote

Configurable por `sync.lote.maximo`. Un lote más grande responde `400` **con el límite en el
mensaje**, para que el front trocee sin tener que adivinarlo ni versionarlo por su cuenta.

El límite no es por tamaño del payload —100 tickets de cinco líneas son unos 70 KB, nada— sino por
**cuánto tiempo el request retiene la base**: cada evento es su propia transacción y toma locks de
fila sobre stock y lotes. 100 eventos son un par de segundos de trabajo secuencial; 200 empiezan a
ser un request del que nadie sabe si se colgó. Para un local de 200 tickets diarios, dos días de
corte son unos 400 eventos: cuatro requests de un par de segundos, que es un perfil sano.

Dos notas: el troceo lo hace el front respetando el orden local, y un lote rechazado por tamaño no
pierde nada —sigue en la cola—. Y si alguna vez hay varias cajas sincronizando a la vez, el tope es
también lo que evita que se coman el pool de conexiones.

### D7 · CERRADA. FEFO diferido, pero solo sobre los lotes que existían a la fecha del ticket

El ticket offline no sabe de lotes, así que el backend aplica FEFO al procesarlo, quizá dos días
después. Se acepta —la alternativa es que el front conozca los lotes, o sea la réplica local de
inventario que el requisito descarta—, con un refinamiento que evita un error innecesario: **se
prefieren los lotes cuyo `created_at` es anterior o igual a la fecha del ticket**. Sin eso, un lote
que ingresó ayer por una compra podría absorber la venta de anteayer, cuando físicamente no estaba
en el local. Si con esos lotes no alcanza, cae en el resto y en la regularización de D3.

**El cómo importa más que el qué.** El bloqueo sigue siendo `findParaDescuentoFefo` tal como está
—una sola consulta, todos los lotes del producto, en el orden del contrato— y el filtro por fecha
se hace **en memoria** sobre lo ya bloqueado. Una segunda consulta que traiga "primero los viejos y
después los nuevos" tomaría los locks en un orden distinto al canónico —un lote ingresado ayer
puede vencer antes que uno viejo—, y ahí se abre justo el ciclo de deadlock que el javadoc de esa
consulta documenta con cuidado. Son unas diez líneas dentro del loop del FEFO y cero cambios en la
puerta de bloqueo.

Un lote que venció entre la venta y la sincronización no necesita tratamiento especial: el FEFO lo
elige primero de todas formas, que es lo correcto, porque es el que se vendió.

### D8 · CERRADA. Las fechas las manda el front; el backend guarda aparte cuándo llegó

Un lote son veinte tickets que pasaron en momentos distintos y llegan todos juntos. Si la fecha la
pone el `INSERT`, los veinte quedan fechados en el mismo segundo y dos días después de lo que
ocurrió: el resumen diario, los reportes de ventas y ganancias y el arqueo del turno quedarían
todos corridos.

**Decisión: `created_at` es la fecha en que el ticket ocurrió, y la manda el front. `deleted_at`
es la fecha en que se anuló, y también la manda el front.** Se agrega una columna nueva,
`sincronizado_en`, para cuándo llegó a MySQL.

Esto es más simple que la idea anterior de agregar un `ocurrido_en` al lado de `created_at`:
**todos los índices, listados y reportes que ya usan `created_at` siguen funcionando sin tocarse**,
porque la columna pasa a significar lo que ellos siempre asumieron que significaba —cuándo se
vendió—. Lo que se agrega es el dato que hoy no existe: cuándo se sincronizó.

| Columna | Qué significa después del cambio | Quién la pone |
|---|---|---|
| `created_at` | Cuándo ocurrió el ticket | El front (online: el reloj del servidor) |
| `deleted_at` | Cuándo se anuló | El front (online: el reloj del servidor) |
| `sincronizado_en` | Cuándo llegó a MySQL. `NULL` en las ventas online | El backend, siempre |
| `updated_at` | Auditoría de la fila | El backend, siempre (`@UpdateTimestamp` se queda) |

**Lo que cuesta.** `@CreationTimestamp` pisa el valor en cada `INSERT`, así que hay que sacarlo de
`Venta` y `DetalleVenta` y poner la fecha explícita en **todos** los caminos de creación, incluido
el online. Y la fecha tiene que bajar por toda la cadena, que es la parte que se descubre tarde:
el movimiento de stock y el movimiento de caja de un ticket offline también tienen que quedar
fechados cuando salió la mercadería, no cuando llegó el lote, o el kardex y el arqueo cuentan otra
historia que la venta. `MovimientoStockRequest` y el registro automático de caja necesitan aceptar
la fecha (ver fase A4).

**Validación obligatoria, porque la fecha ahora es un dato del cliente.** El reloj de una tablet
puede estar en 1970 o en 2030, y una fecha basura envenena los reportes, el orden de los uuid v7 y
la ventana de anulación de D5. Tres reglas, configurables:

- No más de 5 minutos en el futuro (`sync.desfase-maximo-minutos`).
- No más de 60 días en el pasado (`sync.antiguedad-maxima-dias`): más que eso no es un corte de
  internet, es un reloj roto.
- La fecha de anulación nunca anterior a la del ticket.

Un evento que no las cumple responde `ERROR` con el motivo, y el front lo deja para revisión.

### D9 · CERRADA. El total se recalcula en el backend y se compara con tolerancia

El front manda el total, pero el total es plata y viene sumado en otra máquina con `float`. Que el
backend lo guarde sin mirar es confiar en una suma ajena; que lo recalcule y rechace el ticket ante
cualquier diferencia es peor, porque **la venta ya ocurrió** y un centavo de redondeo no puede
frenar la sincronización.

**Decisión:** el backend recalcula `Σ precioUnitario × cantidad` desde los detalles —en el orden
de las líneas del ticket, como ya hace hoy y por el mismo motivo
([VentaService.java:179-184](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L179-L184))—
y lo compara con el declarado:

- Diferencia ≤ 1 centavo: se guarda **el recalculado**. Es redondeo de `float` y no significa nada.
- Diferencia mayor: se guarda el recalculado, el ticket se persiste igual y queda marcado con
  `requiere_revision`. Una diferencia de verdad es un bug del front o un ticket manipulado, y las
  dos cosas se miran, no se descartan.

El `precioUnitario` de cada línea **sí** se toma tal como viene: es el precio que el cliente pagó
en ese momento, no el de la lista de hoy. Es el mismo criterio de snapshot que ya usa el repo.

**La recomendación que va más allá del sync:** esto empeora la deuda de los importes en `float`
—deuda técnica #1 del roadmap, la única que ya produce datos incorrectos—. Hasta ahora sumaba una
sola máquina; a partir de acá suman dos y tienen que coincidir, y cada diferencia de redondeo se
va a presentar como un ticket "para revisar" que en realidad no tiene nada. **Conviene migrar
`ventas.total`, `detalles_ventas.precio_unitario` y `costo_unitario` a `DECIMAL(12,2)` +
`BigDecimal` en esta misma versión**, aprovechando que A1 ya toca esas dos tablas con la aplicación
detenida: la comparación pasa a ser exacta, la tolerancia de un centavo desaparece y nos ahorramos
migrar dos veces la tabla que más crece. Es la única parte de este plan que recomiendo agregar al
alcance en vez de postergar.

---

## 3. El contrato con el front

Es lo único que faltaba acordar antes de codear, y lo que permite que las dos puntas avancen en
paralelo. Queda acá mientras se discute; cuando el endpoint exista, esto baja a
[`api-endpoints.md`](api-endpoints.md) con la forma del resto de la referencia.

### 3.1 El endpoint

```
POST /api/ventas/v1/sync
Authorization: Bearer <access token>
```

Lo puede llamar cualquier usuario autenticado que pueda vender (EMPLEADO o superior). Quien llama
es **quien sincroniza**, no necesariamente quien vendió: esa distinción es D4.

### 3.2 Request

```json
{
  "dispositivo": "caja-01",
  "eventos": [
    {
      "uuid": "01932f4e-8c7a-7000-9b1e-3f5a2d8c4e10",
      "tipo": "ABRIR_SESION",
      "secuencia": 1041,
      "ocurridoEn": "2026-09-10T08:02:11",
      "idUsuario": "0192a1b2-...",
      "saldoInicial": 15000.00
    },
    {
      "uuid": "01932f5a-1b3c-7000-8d2f-9e4a7c1b5d22",
      "tipo": "CREAR",
      "secuencia": 1042,
      "ocurridoEn": "2026-09-10T08:14:53",
      "idVendedor": "0192a1b2-...",
      "idSesion": "01932f4e-8c7a-7000-9b1e-3f5a2d8c4e10",
      "total": 4850.00,
      "metodoPago": "EFECTIVO",
      "montoRecibido": 5000.00,
      "detalles": [
        { "tipo": "PRODUCTO", "idProducto": "0192c3d4-...", "cantidad": 2, "precioUnitario": 1200.00 },
        { "tipo": "MANUAL", "nombreManual": "Bolsa", "cantidad": 1, "precioUnitario": 2450.00 }
      ]
    }
  ]
}
```

**Campos comunes a todo evento**

| Campo | Tipo | Regla |
|---|---|---|
| `uuid` | UUID v7 | La identidad de **la entidad**, no del evento: en `CREAR` y `ANULAR` es el uuid del ticket; en los de caja, el de la sesión. Versión 7 obligatoria (D1) |
| `tipo` | enum | `CREAR` · `ANULAR` · `ABRIR_SESION` · `CERRAR_SESION` |
| `secuencia` | long | Contador monótono del dispositivo. Solo se usa para desempatar dos eventos con el mismo `ocurridoEn` |
| `ocurridoEn` | fecha-hora local | Cuándo pasó en el local. Es la fecha que se persiste (D8) |

`dispositivo` va a nivel del lote —es el mismo para todos sus eventos— y se guarda en cada venta.

**Carga por tipo de evento**

| `tipo` | Campos propios |
|---|---|
| `CREAR` | `idVendedor`, `idSesion`, `total`, `metodoPago` (`EFECTIVO` · `TARJETA` · `TRANSFERENCIA`), `montoRecibido` (solo en efectivo), `detalles[]` |
| `ANULAR` | `idUsuario` (quién anuló), `motivo` (opcional mientras no se cierre esa decisión) |
| `ABRIR_SESION` | `idUsuario`, `saldoInicial` |
| `CERRAR_SESION` | `idUsuario`, `saldoFinal` (el conteo físico), `montoRetirado`, `observaciones` |

Cada línea de `detalles[]`: `tipo` (`PRODUCTO` o `MANUAL`), `idProducto` o `nombreManual` según el
caso, `cantidad` y `precioUnitario`.

**Un supuesto que conviene tener presente:** el evento `CREAR` representa siempre una venta
**cerrada y cobrada** —es lo que es un ticket de caja registradora—, así que no lleva un campo
`cobrada` y su fecha de cobro es la misma `ocurridoEn`. Si algún día el front necesita el ticket
en espera, hay que agregar ese campo *y* eximirlo del barrido de reservas, que a los 120 minutos
anula las ventas sin cobrar.

### 3.3 Response

Siempre `200`, con un resultado por ítem y en el mismo orden en que se procesaron:

```json
{
  "recibidos": 2,
  "resultados": [
    { "uuid": "01932f4e-...", "tipo": "ABRIR_SESION", "estado": "OK" },
    {
      "uuid": "01932f5a-...",
      "tipo": "CREAR",
      "estado": "OK",
      "requiereRevision": true,
      "mensaje": "Se regularizaron 6 unidades de Yerba 1kg"
    }
  ]
}
```

| Campo del resultado | Qué dice |
|---|---|
| `estado` | `OK` o `ERROR` |
| `requiereRevision` | En un `OK`: el evento se persistió pero dejó una discrepancia —stock regularizado (D3) o total que no coincide (D9)—. El front puede mostrarlo, pero **el evento ya está sincronizado**: sale de la cola igual |
| `codigo` | En un `ERROR`: cuál de los casos de 3.4 |
| `reintentable` | En un `ERROR`: si tiene sentido volver a mandarlo. `false` significa que reintentar no va a cambiar nada y el evento necesita intervención |
| `mensaje` | Texto para el operador o para el log |

`reintentable` es lo que le permite al front no reintentar para siempre un evento condenado, que
es justamente la decisión que quedó abierta de su lado.

**Errores del lote entero**, los únicos que no responden `200`:

| Status | Cuándo |
|---|---|
| `400` | JSON ilegible, `eventos` vacío, o más de 100 eventos —con el límite en el mensaje (D6)— |
| `401` | Token ausente, vencido o de un usuario dado de baja |
| `403` | Autenticado sin permiso para vender |

### 3.4 Códigos de error por ítem

| `codigo` | Cuándo | `reintentable` |
|---|---|---|
| `UUID_INVALIDO` | El uuid no es versión 7 (D1) | No |
| `FECHA_INVALIDA` | Futuro, demasiado vieja, o anulación anterior al ticket (D8) | No |
| `VENDEDOR_INEXISTENTE` | El `idVendedor` no existe. Que esté dado de baja **no** es error (D4) | No |
| `PRODUCTO_INEXISTENTE` | Una línea referencia un producto que no existe | No |
| `SESION_INEXISTENTE` | El ticket referencia una sesión que todavía no llegó | **Sí** — puede llegar en el lote siguiente |
| `SESION_YA_ABIERTA` | `ABRIR_SESION` choca con el turno abierto que ya hay (D2) | **Sí** — cuando el otro cierre |
| `SESION_CERRADA` | El ticket llegó después del corte de su propio turno: un corte firmado no se toca (D2) | No — lo resuelve el admin |
| `PERMISO_INSUFICIENTE` | `ANULAR` de un EMPLEADO sobre una venta ajena o de más de 7 días (D5) | No |
| `INTERNO` | Cualquier fallo inesperado del backend | **Sí** |

### 3.5 Idempotencia

La clave es siempre el par `tipo` + `uuid`, nunca un id de evento:

| Evento | Reenviarlo produce |
|---|---|
| `CREAR` | `OK` sin duplicar la venta ni volver a tocar el stock |
| `ANULAR` | `OK` sin efecto si ya está anulada; si el ticket todavía no llegó, queda como anulación pendiente y también responde `OK` |
| `ABRIR_SESION` | `OK` sin abrir un segundo turno |
| `CERRAR_SESION` | `OK` sin recalcular el corte ya hecho |

De ahí que el caso de RNF-09 —el lote se procesó pero la respuesta se perdió— se resuelva
mandándolo de nuevo tal cual, sin ninguna lógica especial de ninguno de los dos lados.

### 3.6 Lo que el backend espera del front

| Obligación | Por qué |
|---|---|
| Generar **UUID v7** para tickets y sesiones | Si no, la PK se fragmenta igual y el evento se rechaza (D1) |
| Mandar `ocurridoEn` con el reloj del local, y mantenerlo en hora | Es la fecha que queda en la base y la que fechan los reportes (D8) |
| Ordenar los eventos por `secuencia` al armar el lote | El backend igual los ordena, pero un lote coherente es más fácil de leer cuando algo falla |
| No mandar más de 100 eventos por request | Arriba de eso el lote entero se rechaza (D6) |
| No borrar un evento de la cola hasta recibir su `OK` | Es lo único que garantiza que nada se pierda (RF-20) |
| Reintentar los `ERROR` con `reintentable: true`, y **no** los de `false` | Reintentar un evento condenado es ruido infinito |
| Incluir el `idSesion` en todo ticket | Sin eso el ticket no se puede persistir (D2) |

### 3.7 Lo que el front puede esperar del backend

- Un `200` con un resultado por cada evento mandado, en el orden en que se procesaron.
- Que un evento con `OK` esté persistido y sea seguro borrarlo de la cola, incluso si trae
  `requiereRevision`.
- Que reenviar un lote entero sea inofensivo.
- Que un `ERROR` en un ítem no afecte a los demás del mismo lote.

---

## 4. Track A — Sincronización offline

### Fase A1 · Esquema e identidad del ticket — **HECHA** (2026-09-12)

**Qué se toca**

- Migración `12_ventas_offline.sql`: en `ventas`, agregar
  `sincronizado_en DATETIME(6) NULL` (D8), `origen VARCHAR(10) NOT NULL DEFAULT 'ONLINE'`,
  `dispositivo VARCHAR(50) NULL`, `id_usuario_sync BINARY(16) NULL` (FK a `usuarios`,
  `ON DELETE RESTRICT`) y `requiere_revision BIT(1) NOT NULL DEFAULT b'0'`. Índice
  `ix_ventas_revision (requiere_revision, deleted_at)`. **No** se agrega `ocurrido_en`: por D8 la
  fecha del ticket es `created_at`, así que los índices y las consultas por fecha quedan como
  están.
- Tabla nueva `anulaciones_pendientes`: `id_venta BINARY(16)` como PK —el uuid del ticket que
  todavía no llegó—, `anulado_en`, `id_usuario`, `motivo VARCHAR(255) NULL`, `created_at`. Sin FK
  a `ventas`, justamente porque la fila puede no existir todavía.
- Migración `13_importes_decimal_ventas.sql` (D9): `ventas.total`,
  `detalles_ventas.precio_unitario` y `detalles_ventas.costo_unitario` a `DECIMAL(12,2)`, con las
  entidades y los DTOs a `BigDecimal`. Es el único punto del plan que agranda el alcance a
  propósito; el motivo está en D9.
- `00_init_limpio.sql` replica todo (la convención del repo: cada migración se escribe dos veces
  hasta que exista Flyway).
- `Venta` y `DetalleVenta`: sacar `@GeneratedValue` —id asignado, v7 por D1— y sacar
  `@CreationTimestamp` —fecha explícita en todos los caminos, por D8—. `@UpdateTimestamp` se queda.
- Validación de uuid: versión 7 obligatoria en el flujo de sync.

**Criterio de aceptación.** La app arranca con `JPA_DDL_AUTO=validate` sin discrepancias —como
está hoy—, el alta online sigue generando su uuid y su fecha en el servidor, y `origen = ONLINE`
en todo lo ya cargado.

**Tests.** Que una venta online siga naciendo con uuid propio y `created_at` del servidor; que un
uuid v4 recibido por sync se rechace; que dos v7 consecutivos queden ordenados; que los totales en
`DECIMAL` no arrastren el redondeo que arrastraban con `float`.

**Cuidado con la migración a `DECIMAL`.** Los datos ya cargados en `float` se convierten con el
redondeo que tengan: conviene que la migración abra con la consulta informativa de rigor —filas
cuyo total no coincide con la suma de sus detalles— para saber qué se está arrastrando antes de
convertir, como hacen las once migraciones anteriores.

**Cómo quedó.** Las dos migraciones se aplicaron contra una base real cargada con el esquema
anterior, y el esquema resultante salió **idéntico byte a byte** al que produce el
`00_init_limpio.sql` nuevo desde cero. La app arranca con `JPA_DDL_AUTO=validate` sin
discrepancias y una venta creada por la API queda con uuid v7, fecha del servidor y total exacto.
270 tests en verde. Cinco cosas que aparecieron al hacerlo:

- **Hibernate 6.6 no genera v7.** `@UuidGenerator` solo ofrece `RANDOM` (v4) y `TIME`, que es un
  v1 con IP en vez de MAC; el `Style.VERSION_7` llegó en Hibernate 7. O sea que el "plan B" de D1
  —el helper propio— era el único camino. Quedó en `shared/Uuid7.java`, con un contador monótono
  en los 12 bits que siguen a la versión: sin eso, dos ids del mismo milisegundo quedan ordenados
  por el azar y el test de monotonía sería una moneda al aire. Cuando el proyecto salte a
  Hibernate 7, la clase se puede tirar.
- **El id asignado a mano hace que Spring Data pierda el `isNew()`.** Al sacar `@GeneratedValue`,
  `save()` deja de ver la entidad como nueva —decide mirando si el id es null— y pasa por
  `merge()`: un `SELECT` que no devuelve nada antes de **cada** `INSERT`. Se vio en el log SQL
  contra la base real, no en los tests con mocks. `Venta` y `DetalleVenta` implementan
  `Persistable<UUID>` con un flag `@Transient` que apagan `@PrePersist` y `@PostLoad`. Sin esto,
  un lote de 100 tickets de A2 haría más de mil consultas de más.
- **La conversión a `DECIMAL` puede descuadrar una venta que en `float` cuadraba**, porque la
  cabecera y las líneas se redondean por separado: un total de `999.999` queda en `1000.00` y su
  línea de `3 × 333.333` en `3 × 333.33 = 999.99`. La migración 13 lo documenta y lista las
  ventas afectadas **después** de convertir —antes no se ven—.
- **`monto_recibido` se migró también**, aunque D9 no lo listaba. Es plata, es de la misma tabla y
  entra en el mismo `ALTER`; dejarlo en `float` obligaba a convertir de ida y vuelta para calcular
  el vuelto y a migrar dos veces la tabla que más crece.
- **La frontera con caja y reportes.** La plata de `compras`, `caja` y `productos` sigue en
  `float`, así que hay puntos donde el importe exacto se degrada al cruzar: el movimiento de caja
  de una venta en efectivo y los acumuladores de los reportes. Todos pasan por
  `shared/Importes.java`, que además evita la trampa de `BigDecimal.valueOf(float)` —ensancha a
  `double` y saca a la luz la basura binaria: `1200.05f` termina en `1200.0499877929688`—. El
  pasaje correcto es por `Float.toString`.

### Fase A2 · Endpoint de lote con resultado por ítem — **HECHA** (2026-09-12)

**Qué se toca.** `POST /api/ventas/v1/sync`, DTOs `SyncLoteRequest` / `SyncLoteResponse` y un
servicio nuevo `SyncVentasService` dentro del módulo `ventas` (implementa la parte de `VentasApi`
que corresponda; el módulo no gana dependencias salientes nuevas).

**Cómo queda.** El request es una lista de eventos
`{ uuid, tipo, ocurridoEn, secuencia, idVendedor, idSesion, dispositivo, ...carga }`, con `tipo` en
`CREAR | ANULAR | ABRIR_SESION | CERRAR_SESION` (los dos últimos entran en A6, pero el contrato
los contempla desde el principio para no versionar el endpoint dos veces). La respuesta es `200`
con un resultado por ítem —`{ uuid, tipo, estado: OK|ERROR, mensaje }`— y nunca un `4xx` global,
salvo payload ilegible o lote por encima del tope (D6). Seis reglas que son el núcleo de la fase:

1. **Orden.** Los eventos se procesan por `ocurridoEn`/`secuencia` ascendente, no por el orden del
   array: es lo que hace que `CREAR` y su `ANULAR` en el mismo lote se apliquen en el orden en que
   pasaron en el local (RF-09), y lo que hace que la apertura del turno llegue antes que sus
   tickets (D2).
2. **Una transacción por evento**, no una por lote. Un lote a medio procesar tiene que dejar la
   base consistente, porque es exactamente lo que pasa cuando la respuesta se pierde (RNF-09).
3. **Idempotencia.** `CREAR` cuyo uuid ya existe responde `OK` sin duplicar ni tocar stock;
   `ANULAR` sobre una venta ya anulada responde `OK`. El reintento del front no puede producir
   efectos nuevos (RF-18, RNF-03).
4. **Fechas validadas** (D8): desfase futuro, antigüedad máxima y anulación posterior al ticket.
   La fecha es ahora un dato del cliente y hay que tratarla como tal.
5. **Total recalculado** (D9), con el ticket marcado para revisión si la diferencia es real.
6. **El error de validación se devuelve, no se traga.** El front lo marca `ERROR_PENDIENTE` y lo
   reintenta; el backend no guarda cola de fallidos.

Y dos validaciones que son fáciles de escribir de más: el `idVendedor` solo tiene que **existir**
—no se exige que esté activo, por D4—, y el tope del lote son 100 eventos con el número en el
mensaje de error (D6).

**Criterio de aceptación.** Reenviar el mismo lote dos veces deja la base idéntica a la primera
vez: mismo stock, mismos movimientos, mismas ventas.

**Tests.** Lote con un evento válido y uno inválido ⇒ `200` con un `OK` y un `ERROR`, y el válido
persistido; lote reenviado completo ⇒ sin duplicados; lote desordenado ⇒ se aplica en orden
local; lote de 101 eventos ⇒ `400` con el límite en el mensaje; fecha 3 días en el futuro ⇒
`ERROR` en ese ítem; total declarado que no coincide ⇒ persistido y marcado; ticket de un vendedor
dado de baja después de la venta ⇒ se persiste igual.

**Cómo quedó.** 26 tests nuevos y un lote real contra MySQL —seis eventos desordenados, con un v4
colado, una fecha futura, un total descuadrado y un `ANULAR`— mandado dos veces: la segunda no
duplicó ni ventas ni movimientos de stock. Cuatro cosas para tener presentes:

- **El descuento de stock se extrajo a `DescontadorStock`.** Los dos caminos que venden —el
  mostrador y el lote— hacen exactamente lo mismo con el inventario, y es la parte que toma locks
  de fila sobre lotes: dos copias del loop es la forma más probable de que un día tomen los locks
  en órdenes distintos y la base empiece a matar transacciones por deadlock. Además le deja a A5
  un solo lugar donde meter la regularización de D3.
- **Una transacción por evento obligó a partir el servicio en dos.** `SyncVentasService` orquesta
  —ordena, acota el lote, arma los resultados— y `ProcesadorEventoSync` aplica cada evento con su
  propio `@Transactional`. No alcanzaba con un método privado: una llamada interna no pasa por el
  proxy de Spring y no abriría ninguna transacción nueva. El error de evento viaja como excepción
  justamente para que, al salir de ese método, se deshaga todo lo que el evento hubiera escrito.
- **La idempotencia tiene que devolver la misma respuesta, no solo evitar el duplicado.** El
  primer intento devolvía `OK` pelado al reenviar un ticket marcado para revisión, y eso hacía que
  el único escenario donde la idempotencia importa —la respuesta original se perdió— fuera el
  único donde el front nunca se entera de la marca. Ahora el reenvío relee la venta y repite el
  flag.
- **`STOCK_INSUFICIENTE` es un código transitorio.** Contradice el principio de D3 —una venta
  física ya ocurrió y no se puede rechazar por lo que diga el inventario— y existe solo hasta A5.
  Es el motivo por el que A5 no se puede postergar mucho: en un corte real, un faltante de stock
  es más la regla que la excepción.

**Dos limitaciones conocidas, que resuelven las fases siguientes.** El movimiento de caja de un
ticket offline se fecha con el reloj del servidor y no con el del ticket (**A4**), y los eventos
`ANULAR`, `ABRIR_SESION` y `CERRAR_SESION` responden `NO_IMPLEMENTADO` reintentable (**A3** y
**A6**), que es el comportamiento que RF-10 pide para que el front pueda trabajar contra el
endpoint desde ya.

### Fase A3 · Anulación: evento, permisos y reversa de caja — **HECHA** (2026-09-12)

**Qué se toca.** El caso `ANULAR` del servicio de sync, `AnulacionPendienteRepository`, el
`delete` de `VentaService` (que hoy rechaza cobradas) y `SecurityConfig.java:76`.

**Cómo queda.** Tres escenarios, y el segundo es el que no existe hoy:

| Escenario | Qué hace el backend |
|---|---|
| El ticket ya está persistido | Anula: reversa de stock por `movimientos_stock` —la maquinaria actual—, borrado lógico de cabecera y detalles con la fecha de anulación del front (D8), y reversa de caja si hubo efectivo |
| El ticket todavía no llegó | Guarda la fila en `anulaciones_pendientes` y responde `OK`; al persistirse el `CREAR`, la aplica en la misma transacción |
| Ya estaba anulado | `OK`, sin efecto |

Los permisos de D5 se implementan una sola vez, en `VentaService.delete`, y los dos caminos —sync
y panel— pasan por ahí: dueño y ventana de 7 días para EMPLEADO, sin límite para ADMIN. La
anulación de una venta cobrada queda habilitada, y la reversa de caja va al turno abierto de hoy
con `OrigenMovimientoCaja.REVERSA`, o la anulación se rechaza si no hay turno abierto (D5).

**Criterio de aceptación.** `ANULAR` que llega antes que su `CREAR` termina con el ticket
persistido y anulado, con stock repuesto exactamente una vez. Un EMPLEADO no puede anular la venta
de otro ni una de hace 8 días, ni por sync ni por el panel.

**Tests.** Los tres escenarios; `ANULAR` huérfano que nunca recibe su `CREAR` (la fila queda
pendiente y no rompe nada); anulación de venta con FEFO repartido entre lotes ⇒ cada lote recupera
su cantidad exacta; EMPLEADO sobre venta ajena ⇒ `403`; EMPLEADO sobre sesión de hace 8 días ⇒
`400`; ADMIN sobre la misma ⇒ anulada; venta en efectivo de un turno cerrado ⇒ `REVERSA` en el
turno de hoy y el corte viejo intacto; sin turno abierto ⇒ `400`.

**Cómo quedó.** 340 tests en verde y todo el criterio de aceptación verificado contra MySQL. El
caso que da nombre a la fase: un `ANULAR` mandado **en un lote** y su `CREAR` **en el siguiente**
terminó con el ticket persistido y anulado, `anulaciones_pendientes` vacía, el detalle dado de
baja, el stock repuesto exactamente una vez y las tres fechas distintas y correctas —`created_at`
dos horas atrás, `deleted_at` una hora atrás, `sincronizado_en` ahora—. D5 se probó por las dos
puertas: por el panel un vendedor anuló su propia venta cobrada (`204`), rebotó en la ajena
(`403`) y el admin anuló esa misma; por sync, con el turno movido a hace ocho días, el vendedor
recibió `PERMISO_INSUFICIENTE` y el admin `OK`. Y la reversa de caja quedó en el turno abierto
nuevo mientras el corte viejo siguió firmado e intacto. Tres apuntes:

- **La anulación se extrajo a `AnuladorVentas`**, por el mismo motivo que el descuento en A2:
  ahora hay **tres** puertas que anulan —el panel, el evento `ANULAR` y el barrido de reservas
  vencidas— y tienen que hacer lo mismo. Sobre todo tienen que aplicar los mismos permisos: si la
  puerta de sincronización fuera más permisiva, anular offline sería la forma de saltear el
  permiso. `VentaService` perdió de paso tres dependencias que dejaron de usarse.
- **El permiso del evento se resuelve sobre quien anuló en el local, no sobre quien sincroniza.**
  Es la misma distinción de D4 y obliga a preguntar el rol vigente con `usuarioApi.rolVigente`,
  porque el `SecurityContext` tiene al que está logueado ahora, que no es el mismo.
- **La fila pendiente se consume pase lo que pase.** Si la anulación no se puede aplicar al llegar
  el `CREAR` —por permisos o por falta de turno abierto—, dejarla esperando haría que el mismo
  error se repitiera en cada `CREAR` reenviado, y el ticket nunca terminaría de entrar.

**Con esto queda cerrado el pendiente que dejó A5:** el ticket con stock regularizado repone al
anularse las 10 unidades que salieron y no las 4 que el sistema creía tener, porque la reversa
trabaja sobre los movimientos `VENTA` y esos quedaron por el ticket completo.

### Fase A4 · La fecha del front, propagada por toda la cadena — **HECHA** (2026-09-12)

**Qué se toca.** `MovimientoStockRequest` y el registro automático de caja, que hoy no aceptan
fecha porque nunca la necesitaron.

**Por qué es una fase aparte.** Por D8, la venta ya queda fechada cuando ocurrió, y los reportes y
los índices no se tocan —que es justamente la ventaja de haber elegido `created_at` en vez de una
columna nueva—. Pero la venta no es la única fila que se escribe: cada ticket genera movimientos de
stock y, si fue en efectivo, un movimiento de caja. Si esos quedan fechados cuando llegó el lote,
**el kardex muestra la mercadería saliendo dos días después de la venta que la sacó** y el
movimiento de caja entra al turno con la fecha equivocada. Es el efecto colateral que se descubre
tarde, y por eso tiene su propia fase.

La fecha viaja como un parámetro más desde el evento hasta cada `INSERT`. En el flujo online el
parámetro va nulo y vale el reloj del servidor, así que nada cambia para lo que ya funciona.

**Criterio de aceptación.** Un ticket offline de anteayer aparece en el reporte de anteayer, y sus
movimientos de stock y de caja están fechados anteayer, no hoy.

**Tests.** Resumen diario con un ticket offline sincronizado hoy pero ocurrido ayer; kardex de un
producto vendido offline ⇒ el movimiento lleva la fecha de la venta; reporte de ganancias por
rango que incluye tickets sincronizados fuera de su rango de llegada.

**Cómo quedó.** 345 tests en verde y el criterio de aceptación verificado contra MySQL: un ticket
sincronizado hoy pero ocurrido el 10 dejó las **tres** filas fechadas el 10 —la venta, su
movimiento de stock y su movimiento de caja—, con `sincronizado_en` como único campo que dice
cuándo llegó. El resumen diario del 10 muestra la venta y el de hoy no; el reporte de ganancias la
ubica en el 10. Tres apuntes:

- **No se sacó `@CreationTimestamp` a cambio de fechar a mano en los 18 lugares que crean
  movimientos.** La mayoría son caminos online —compras, alta de producto, ajustes manuales— que
  no tienen ninguna fecha que propagar, y obligarlos a pasarla haría que cualquier call site
  futuro que se olvide muera contra un `NOT NULL`. En su lugar quedó un `@PrePersist` que fecha
  solo si nadie lo hizo, que es exactamente la semántica que este documento pedía: *"en el flujo
  online el parámetro va nulo y vale el reloj del servidor"*.
- **Hay una fecha que a propósito NO sigue al front: la `REVERSA` de caja.** Por D5 la plata de
  una anulación sale del turno abierto de hoy, así que el movimiento tiene que caer dentro de la
  vida de ese turno. Fechándolo anteayer, el arqueo del turno lo contaría —suma por sesión— pero
  el resumen de caja por fecha no, y las dos vistas dirían cosas distintas. La reversa de
  **stock** sí lleva la fecha de la anulación: la mercadería volvió a la góndola ese día.
- **`CajaApi` cambió de firma** en sus dos movimientos automáticos. Es una interfaz interna entre
  módulos, no de la API HTTP, así que no rompe nada del contrato con el front.

### Fase A5 · Faltantes de stock y conciliación — **HECHA** (2026-09-12)

**Qué se toca.** El descuento de stock del flujo de sync —que ya no puede reventar con `400`
([InventarioService.java:88-90](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/inventario/service/InventarioService.java#L88-L90),
[VentaService.java:140](../src/main/java/com/SolucionesInformaticasBA/minimarket/modules/ventas/service/VentaService.java#L140))—,
el loop del FEFO, el flag `requiere_revision`, y un `GET /api/ventas/v1/revision` paginado (ADMIN)
que lista lo que quedó marcado: regularizaciones de stock (D3) y diferencias de total (D9).

**Cómo queda.** Dos cosas, las dos dentro del descuento de stock del flujo offline:

- **La regularización de D3.** Si la existencia no alcanza, primero entra el `AJUSTE` por la
  diferencia y después la `VENTA` completa. El stock nunca pasa por un valor negativo en ningún
  paso intermedio, y la suma de los movimientos sigue dando el cambio real de la existencia.
- **El FEFO por fecha de D7.** Se bloquean todos los lotes con la consulta de siempre y se elige en
  memoria: primero los que existían a la fecha del ticket, después el resto. La puerta de bloqueo
  no se toca.

**Criterio de aceptación.** Un ticket offline que pide 10 unidades con 4 en stock se persiste
completo, deja el stock en 0, deja en el kardex un `AJUSTE +6` y una `VENTA -10`, y aparece en el
listado de revisión. Si después se anula, el stock queda en 10.

**Tests.** Regularización total y parcial, con producto común y con producto por lotes; producto
con lotes y sin ningún lote cargado ⇒ se crea el lote `SIN_FECHA`; anulación de un ticket
regularizado ⇒ repone las 10, no las 4; venta de anteayer con un lote ingresado ayer ⇒ descuenta
del lote viejo; que el listado de revisión no muestre las ventas online normales.

**Cómo quedó.** 312 tests en verde, y el criterio de aceptación verificado contra MySQL tal cual
está escrito: con 4 en stock, un ticket de 10 dejó el stock en 0, el kardex con
`AJUSTE +6 · Regularización por venta offline <uuid>` seguido de `VENTA -10`, y la venta en el
listado de revisión. El caso de lotes se probó también: un ticket de 200 sobre 96 disponibles dejó
la suma del kardex en **−96** —lo que realmente bajaron los lotes— y los movimientos `VENTA`
sumando **200**, que es lo que repondría la anulación. Esa diferencia es justo el argumento de D3
para registrar la venta completa en vez de la parcial. Cuatro apuntes:

- **La regularización de un producto común vive en `InventarioService`**, no en el módulo de
  ventas, porque es el único lugar donde se puede decidir cuánto ajustar con la fila de stock ya
  bloqueada. Leerla por fuera para calcular el faltante y después ajustar abriría exactamente la
  carrera que ese lock existe para cerrar.
- **El FEFO por fecha se resolvió con una partición en memoria** sobre lo que
  `findParaDescuentoFefo` ya bloqueó, y hay un test que verifica que no aparezca ninguna consulta
  de bloqueo nueva. Es la parte del diseño que más fácil se rompe sin darse cuenta: una segunda
  consulta "ordenada por fecha de alta" parece inofensiva y abre el ciclo de deadlock.
- **`STOCK_INSUFICIENTE` se eliminó del contrato**, como estaba anotado en A2. Un ticket offline
  ya no se puede rechazar por lo que diga el inventario.
- **La parte del criterio que dice "si después se anula, el stock queda en 10" no se pudo ejercer
  por la API**, porque anular una venta cobrada sigue prohibido hasta A3. Lo que sí está
  verificado es la condición de la que depende: los movimientos `VENTA` del ticket suman el ticket
  completo, y es sobre esos movimientos que trabaja `revertirStock`.

### Fase A6 · La sesión de caja, sincronizada — **HECHA** (2026-09-15)

Es la fase que agrega D2, y la que hace verdadero el RNF-01: sin ella, un turno que abre durante
el corte no tiene dónde colgar sus tickets.

**Qué se toca.** `CajaApi` gana la apertura y el corte con id y fechas provistos —hoy los dos
resuelven todo con `LocalDateTime.now()` y con el id que genera Hibernate—, el servicio de sync
suma los casos `ABRIR_SESION` y `CERRAR_SESION`, y `ventas.id_sesion` pasa a poblarse desde el
evento en vez de con `getIdSesionActiva()`.

**Cómo queda.** Dos pasos, y conviene entregarlos en ese orden porque el primero ya sirve solo:

1. **El ticket declara su sesión.** El evento trae `idSesion` y el backend lo usa, exigiendo que
   esa sesión exista y siga abierta. Cubre el caso frecuente: el turno abrió a la mañana con
   internet y el corte llegó a media tarde.
2. **La apertura y el corte son eventos.** El front genera el uuid v7 del turno, y
   `ABRIR_SESION` —con saldo inicial y fecha— y `CERRAR_SESION` —con el conteo físico, el retiro y
   las observaciones— viajan en el lote. El backend calcula el saldo esperado al procesar el corte,
   con las ventas del turno ya persistidas por el orden de los eventos.

**Lo que no cambia:** `registrarEntradaAutomatica` sigue exigiendo la sesión abierta, y
`uk_sesiones_una_abierta` sigue garantizando un solo turno abierto. Las dos son las invariantes que
evitan que un corte firmado se mueva.

**Criterio de aceptación.** Un turno completo generado offline —apertura, cinco tickets, corte—
sincroniza en un solo lote y queda en MySQL con el mismo saldo esperado y la misma diferencia que
habría tenido online.

**Tests.** El turno completo en un lote; lote con los eventos desordenados ⇒ mismo resultado; un
ticket que llega después del `CERRAR_SESION` de su turno ⇒ `ERROR` y el corte intacto;
`ABRIR_SESION` cuando ya hay otro turno abierto ⇒ `ERROR` claro, no violación de índice cruda.

**Cómo quedó.** 358 tests en verde y el criterio de aceptación verificado contra MySQL: un turno
completo —apertura 08:00, cinco tickets entre las 09:30 y las 13:30, corte 14:00— sincronizado en
un solo lote quedó con `saldo_esperado 27500` (= 15000 + 5 × 2500), contado 27500 y **diferencia
0**, que es exactamente lo que habría dado online. Mandado con los eventos en orden aleatorio, el
resultado es idéntico. Los cuatro escenarios del plan dan lo que tienen que dar, incluido el
ticket tardío, que responde `SESION_CERRADA` sin tocar el corte firmado.

- **`sesiones_caja` se suma a las tablas con id asignado.** D1 acotaba la unificación a `ventas` y
  `detalles_ventas`, pero esta tabla pasó a recibir ids del front, así que le corresponde el mismo
  trato: uuid v7 —también cuando lo genera el backend al abrir online—, `Persistable` para que
  `save()` no vuelva a hacer un `SELECT` antes de cada `INSERT`, y nada de `@GeneratedValue`.
- **La comprobación de "ya hay un turno abierto" se mantiene explícita** aunque
  `uk_sesiones_una_abierta` también lo impida. Una violación de índice sale como un error opaco de
  la base y el front no podría distinguirla de un fallo cualquiera; con la comprobación, el evento
  responde `SESION_YA_ABIERTA` reintentable y el front sabe que basta con esperar.
- **La verificación contra la base encontró algo que los tests con mocks no veían:** el movimiento
  de `RETIRO` del corte se fechaba con el reloj del servidor, así que en un turno sincronizado caía
  **fuera de la vida de su propio turno** —el corte era de anteayer a las 14:00 y el retiro figuraba
  hoy—. Ahora lleva la fecha del cierre, que es cuando la plata salió del cajón. Es el mismo
  problema que A4 resolvió para los movimientos de venta, en un lugar que A4 no había tocado.

### Fase A7 · Documentación y cierre — **HECHA** (2026-09-15)

`docs/api-endpoints.md` con el endpoint de sync y el de revisión —request, response por ítem y
errores—; `ARCHITECTURE.md` con el flujo offline, la excepción de identidad de D4, la decisión de
UUIDv7 de D1 y el significado nuevo de `created_at`/`deleted_at` (D8), que es de las cosas que más
confunden a quien lea la base a mano; `CHANGELOG.md` 0.6.0 con los cambios que rompen
compatibilidad y las migraciones `12`–`13`; y el documento de requisitos, con las decisiones
pendientes ya resueltas y la ventana de anulación corregida a 7 días.

**Cómo quedó.** `api-endpoints.md` suma los dos endpoints nuevos con su contrato completo y
corrige lo que cambió en los que ya estaban —los importes en `decimal`, los cuatro campos nuevos
de `VentaResponse`, la matriz de permisos y el `DELETE` de ventas—. `ARCHITECTURE.md` gana la
sección *Ventas creadas sin conexión*, organizada alrededor de **las cuatro reglas que el flujo
offline rompe a propósito**: el id lo genera el cliente, el vendedor viaja en el payload, las
fechas las manda el dispositivo y un faltante de stock no rechaza el ticket. Es la forma que más
sirve para quien vuelva en seis meses: cada una dice contra qué regla general va y por qué.
`CHANGELOG.md` tiene la entrada 0.6.0 con los siete cambios incompatibles y las dos migraciones, y
el `pom.xml` pasó a `0.6.0` —que además es de donde B3 va a sacar el tag de la imagen—.

**Con esto el Track A está completo.** Lo que queda de la 0.6.0 es B3 y B4, que no dependen de
código sino de decisiones de infraestructura.

---

## 5. Track B — Despliegue (los dos pendientes de `ideas.md`)

No depende del Track A y no toca código de negocio. La fase B2 conviene hacerla primero de todo,
por lo barata que es y porque protege la cirugía del Track A.

> ### La regla de ramas, que manda sobre todo el track
>
> **Producción es `main`, siempre.** Un cambio en `main` actualiza la API en el VPS; un cambio en
> `developer` **no** despliega nada. `developer` es donde se integra y se prueba, y la única forma
> de llegar a producción es un merge a `main`.
>
> En la práctica, eso separa los workflows en dos clases y conviene no mezclarlas:
>
> | Workflow | Ramas en las que corre | Qué hace |
> |---|---|---|
> | `ci.yml` (B2) | `main` y `developer`, y los PR contra ellas | Compila y corre los tests. No publica ni despliega. Corre en `developer` justamente para enterarse de que algo se rompió **antes** de que llegue a `main` |
> | Publicación y deploy (B3, B4) | **Solo `main`** | Construye la imagen, la publica y actualiza el VPS |
>
> Los jobs de B3 y B4 llevan `if: github.ref == 'refs/heads/main'` además del filtro de `on.push`,
> que es el cinturón y los tirantes para que un `workflow_dispatch` desde otra rama no despliegue
> por accidente.

### Fase B1 · Imagen Docker — **HECHA** (2026-09-12)

`Dockerfile` multi-stage: `maven:3.9-eclipse-temurin-21` para construir con el wrapper del repo,
`eclipse-temurin:21-jre` para correr, usuario no root, y la configuración por variables de entorno
que ya existe (`.env.example` es la lista completa). `.dockerignore` con `target/`, `.git/` y
`.env`. Un `compose.yaml` con la app más MySQL 8 y un volumen para los datos, que también sirve
para levantar el entorno local sin instalar MySQL.

**Pendiente a resolver acá:** el contenedor arranca con `JPA_DDL_AUTO=none` y la base ya migrada;
mientras no exista Flyway, las migraciones siguen aplicándose a mano y con la app detenida
—limitación conocida, anotada en el roadmap—. Conviene dejarlo escrito en el README del despliegue
para que nadie espere que el contenedor migre solo.

**Criterio de aceptación.** `docker compose up` levanta la app contra una base nueva sembrada con
`00_init_limpio.sql` + `01_seed.sql`, y el login responde.

**Cómo quedó.** Criterio verificado de punta a punta: `docker compose up --build` contra un volumen
vacío, login del admin del seed con `200` y token. La imagen corre como uid 1001 y pesa 580 MB;
bajarla exigiría Alpine o jlink, y Alpine se descartó por ahora porque no trae `tzdata` y la zona
horaria acá importa (ver abajo). Cuatro cosas que aparecieron al probarla y no estaban previstas:

- **`lombok.config` tiene que entrar a la imagen.** Copiar solo `pom.xml` y `src/` compila un jar
  que arranca y muere con *the dependencies of some of the beans form a cycle* entre
  `usuarioService` y `authService`: sin ese archivo, Lombok no copia el `@Lazy` del campo al
  constructor que genera y el ciclo deja de estar roto. Es el modo de falla más caro de diagnosticar
  de todo el track, porque el build es verde y el error aparece recién en runtime.
- **La zona horaria del contenedor.** Por defecto es UTC, y eso fecharía cada venta, cada movimiento
  de caja y cada corte tres horas adelantados. El compose fija
  `TZ=America/Argentina/Buenos_Aires`. **Hay que fijarla también en el VPS (B4)**, y es un dato que
  se vuelve más delicado con el Track A, donde las fechas ya son un dato del cliente (D8).
- **El charset del cliente de MySQL.** Los scripts de `/docker-entrypoint-initdb.d/` los corre el
  cliente del contenedor, que sin locale cae en latin1 y deja "Almacén" guardado como `Almacén`
  —el mismo problema que el README documenta para la carga a mano—. Se resuelve con
  `docker/mysql/utf8mb4.cnf` montado en `/etc/mysql/conf.d/`.
- **El seed no puede ir a producción.** `compose.yaml` quedó como el compose de desarrollo: siembra
  `01_seed.sql` —usuarios con contraseñas conocidas— y publica el 3306. El de producción es otro
  archivo, vive en el VPS y baja la imagen en vez de construirla (B4).

### Fase B2 · CI en cada push y PR — **HECHA** (2026-09-12)

Workflow `.github/workflows/ci.yml`: checkout, JDK 21 con cache de Maven, `./mvnw -B verify`. Los
258 tests son unitarios con mocks, así que corren sin base; el único que necesita MySQL es
`MinimarketApplicationTests.contextLoads`, que hay que excluir del CI o darle un servicio MySQL
—excluirlo es lo razonable hasta que existan tests de integración con Testcontainers—.

**Criterio de aceptación.** Un PR con un test roto no se puede mergear.

**Cómo quedó.** El workflow corre en push a `main` y `developer` y en PR contra ellas, con
`concurrency` para cancelar runs superados y los reportes de surefire subidos como artefacto. La
exclusión se hizo con `@Tag("integracion")` sobre `MinimarketApplicationTests` y
`-DexcludedGroups=integracion` en el comando, en vez de nombrar la clase: así el día que haya
tests con Testcontainers alcanza con ponerles el mismo tag. Falta un paso manual que no está en
el repo: marcar el check *Build y tests* como obligatorio en la protección de rama de GitHub, sin
lo cual el workflow reporta pero no bloquea el merge.

### Fase B3 · Publicación de la imagen

Workflow que **solo en el push a `main`** —o en un tag `v*`— construye y publica en Docker Hub, con
`docker/login-action` y `docker/build-push-action`, tags `latest` y la versión del `pom.xml`.
Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`. Un push a `developer` no publica nada: la imagen
que existe en el registry es, por definición, lo que está o va a estar en producción.

### Fase B4 · Despliegue al VPS y nginx

Job de deploy —**solo `main`**, encadenado a B3— que entra por SSH (`appleboy/ssh-action`, con
`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`) y corre `docker compose pull && docker compose up -d` en el
directorio del proyecto. El `.env` de producción vive en el VPS y **no** se versiona.

nginx como reverse proxy al `8080` del contenedor, con TLS por certbot, `client_max_body_size`
holgado —los lotes de sync son el request más grande del sistema— y los timeouts acordes.
`CORS_ALLOWED_ORIGINS` pasa a apuntar al dominio real del front.

**Lo que B1 deja pedido para acá:** el compose de producción es un archivo propio —el del repo
siembra usuarios de desarrollo y publica el 3306—, tiene que fijar `TZ` igual que el de desarrollo
y conviene que la base corra con un usuario dedicado en vez de `root`.

**Criterio de aceptación.** Un merge a `main` deja la versión nueva corriendo en el VPS sin
intervención manual, y el rollback es volver al tag anterior de la imagen.

**Decisión abierta:** healthcheck. Hoy no hay actuator en el `pom.xml`; sin un endpoint de salud,
el `docker compose up -d` no puede esperar a que la app esté lista y nginx puede servir 502 unos
segundos. Agregar `spring-boot-starter-actuator` con solo `/actuator/health` expuesto es la vía
corta, y hay que decidir si se hace acá.

---

## 6. Orden sugerido

1. **B2** (CI). Una tarde, y todo lo que sigue queda protegido.
2. **Acordar el contrato con el front**: uuid v7, fechas, sesión de caja y forma del evento. Es lo
   único que queda por hacer antes de codear, y condiciona las dos puntas.
3. **A1**. Esquema, uuid v7, fechas explícitas e importes en `DECIMAL`: toda la cirugía de tablas
   junta y con la aplicación detenida una sola vez.
4. **A2 → A3**. El lote y la anulación. Es lo que desbloquea al front.
5. **A4**. Antes de que haya datos offline en producción, o el kardex y el arqueo nacen corridos.
6. **A6** (caja). Se puede solapar con A5; el paso 1 de A6 —el ticket declara su sesión— conviene
   hacerlo junto con A2, porque es un campo más del mismo evento.
7. **A5**, **B1**, **B3**, **B4** en cualquier orden.
8. **A7**. Cierre de documentación y `CHANGELOG` 0.6.0.

El front puede empezar contra A2 en cuanto el contrato del endpoint esté definido, incluso antes
de que A3 y A6 estén terminadas: los eventos que el backend todavía no maneja responden `ERROR` y
el front los reintenta, que es justo el comportamiento que RF-10 pide.

---

## 7. Lo que queda afuera de esta versión

Nada de esto está en `roadmap.md` todavía: se anota acá mientras la 0.6.0 esté abierta, y lo que
siga siendo relevante al cerrarla pasa allá.

- **Credenciales por dispositivo.** Hoy el JWT identifica a una persona, no a una terminal, y por
  eso el endpoint de sync tiene que aceptar el vendedor en el payload y confiar en él (D4). Un
  token por caja —que es lo que realmente sincroniza— cerraría ese hueco y, de paso, resolvería el
  login offline de la lista de abajo.
- **Varias cajas offline en simultáneo** (RNF-08). `uk_sesiones_una_abierta` permite un solo turno
  abierto en todo el sistema: con dos cajas offline, la segunda apertura choca al sincronizar. Pasar
  el índice a "una abierta por caja" es la decisión de *varias cajas o sucursales* del roadmap, y no
  es técnica: depende de cómo rinda el comercio. Mientras sea un local con una caja, no hay problema.
- **Flyway.** Este plan agrega dos migraciones más a las once que ya se aplican a mano, y cada una
  se escribe dos veces. La deuda no se salda acá, pero se agrava: conviene saldarla antes de que
  el sync sume más cambios de esquema.
- **Número de comprobante legible.** El documento de requisitos lo deja pendiente; con uuid en la
  PK no hay correlativo, y si el negocio lo necesita es un trabajo aparte —y probablemente el
  mismo que pide la facturación electrónica del roadmap—.
- **Purga de los 47 días.** Es del front: en el backend la venta no se purga nunca.
- **Login offline.** El access token dura 24 h y el refresh 30 días, así que tras dos días de
  corte el front necesita poder autenticar contra su base local y refrescar al volver la conexión.
  Es trabajo del front, pero condiciona el plazo máximo de corte tolerable: **30 días**, no dos.

---

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| El front genera UUIDv4 y la PK se fragmenta igual | Validar la versión del uuid al recibirlo (A1) y acordarlo por escrito con el front antes de A2 |
| El kardex y el arqueo quedan corridos por la fecha de sincronización | A4 antes de que haya tickets offline en producción |
| Un reloj mal configurado en la tablet envenena reportes y ventanas | Las tres validaciones de fecha de D8, con el evento marcado `ERROR` en vez de aceptado |
| Un lote grande tras un corte largo tumba el request | Tope de 200 eventos (D6) y `client_max_body_size` en nginx (B4) |
| Diferencias de redondeo entre el `float` del front y el del backend generan ruido de "revisión" | Migrar los importes de ventas a `DECIMAL` en A1 (D9), aprovechando que la tabla ya se toca |
| Un ticket offline se cuela en un corte ya firmado | La sesión abierta sigue siendo obligatoria para todo movimiento de caja; el ticket tardío responde `ERROR` (D2) |
| La anulación de una venta cobrada de un turno cerrado descuadra el arqueo de hoy | La `REVERSA` entra al turno abierto, que es de donde sale la plata; sin turno abierto no se anula (D5) |
| Un EMPLEADO atribuye por sync un ticket a otro vendedor | Auditoría, no permiso: `id_usuario_sync` y `origen = OFFLINE` lo dejan a la vista, y el vendedor ve en su listado una venta que no hizo (D4). El cierre real son credenciales por dispositivo |
| La regularización de stock de D3 tapa un robo o una merma bajo la etiqueta "venta offline" | El movimiento queda en el kardex con su motivo y el producto entra al listado de revisión: lo que corresponde es contarlo, no darlo por bueno |
