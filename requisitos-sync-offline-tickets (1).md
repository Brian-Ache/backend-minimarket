# Requisitos del sistema — Sincronización offline-first de tickets

## Contexto

Sistema de punto de venta con base de datos local (SQLite) y base de datos central en la nube (MySQL). El sistema debe seguir operando con normalidad ante cortes de internet de hasta varios días, sincronizando los datos generados localmente mediante envío por lote en cuanto la conexión lo permite. Es un sistema interno, sin requisito de nota de crédito fiscal.

**Alcance del almacenamiento local:** lo único que el front persiste en SQLite son los tickets (cola de envío e historial de consulta). **El stock no existe en local**: vive exclusivamente en MySQL, donde el backend lo descuenta y lo repone con su lógica de lotes/FEFO, movimientos de stock y locks de fila. El front no replica, no cachea y no descuenta stock.

---

## 1. Requisitos funcionales

### 1.1 Registro de ventas
- RF-01: El sistema debe permitir crear un ticket de venta sin conexión a internet.
- RF-02: El ticket se crea siempre en el front, con su `uuid` asignado en el momento de creación (offline-first). El backend ya no es quien origina el ticket ni su identificador.
- RF-03: Cada ticket debe registrar su cabecera (fecha de la venta, total, vendedor, sesión de caja, medio de pago, estado) y su detalle (productos, cantidades, precios).
- RF-03b: El ticket debe registrar a qué **sesión de caja** pertenece, generada también por el front (RF-27). Es lo que permite que el arqueo cubra lo cobrado sin internet en vez de dejarlo para conciliar a mano.
- RF-04: El vendedor debe poder consultar las ventas de los últimos 47 días sin depender de la nube.
- RF-05: La creación de un ticket debe escribir en `ticket_queue` y `ticket_historial` dentro de una única transacción SQLite: si falla cualquiera de las dos escrituras, ninguna debe persistir.
- RF-05b: La creación de un ticket no toca stock en local (no hay stock en local). El descuento de stock lo hace el backend al persistir el evento `CREAR`, con su lógica actual de lotes/FEFO y movimientos de stock.

### 1.2 Envío por lote al backend
- RF-06: El front no envía cada ticket individualmente; los agrupa y los manda en paquetes (lotes) al backend.
- RF-07: Estando online, el envío del lote se dispara por **tiempo transcurrido O cantidad acumulada, lo que ocurra primero** (ej. cada N segundos o al juntar M tickets).
- RF-08: El backend debe procesar cada evento del lote de forma independiente y devolver un resultado por ítem (no un resultado único para todo el lote).
- RF-09: Dentro de un mismo lote, los eventos relacionados (creación de un ticket y su posterior anulación) deben procesarse en el orden en que ocurrieron localmente.
- RF-10: Si un evento falla por error de validación (no de red), no se descarta: el error se registra localmente y el evento se reintenta automáticamente en el siguiente ciclo de envío de lote.

### 1.3 Anulación de ventas
- RF-11: El vendedor puede anular **un ticket propio** cuya sesión de caja abrió hace **7 días o menos**, esté o no ya sincronizado con el backend, con o sin conexión. Los 47 días de `ticket_historial` son la ventana de **consulta**, no la de anulación: el vendedor ve un ticket de hace un mes pero no puede anularlo.
- RF-11b: Un ADMIN o SUPERADMIN anula cualquier ticket, propio o ajeno, sin ventana de tiempo. La regla vale igual por el endpoint de sincronización que por el panel: no puede haber una puerta más permisiva que la otra, o anular offline se vuelve la forma de saltear el permiso.
- RF-11c: Si el ticket se cobró en efectivo, anularlo devuelve plata. Esa devolución se registra en el **turno de caja abierto al momento de anular**, no en el turno original —que puede estar cerrado y su corte firmado—. Sin ningún turno abierto, la anulación se rechaza: no hay caja de donde sacar la plata.
- RF-12: Por ahora el sistema **solo permite anular**, no modificar un ticket ya creado.
- RF-13: Al anular, se lee el ticket desde `ticket_historial` (fuente de lectura/selección) y se inserta un **evento nuevo** `ANULAR` en `ticket_queue`, referenciando el `uuid` del ticket original — independientemente de si ese ticket ya fue enviado o sigue pendiente de envío.
- RF-14: `ticket_historial` se actualiza de inmediato y de forma local (optimista) al anular, sin esperar confirmación del backend.
- RF-15: Al anular, la reposición de stock la hace el backend al procesar el evento `ANULAR` (revirtiendo los movimientos que registró la venta). El front no repone stock, porque no lo tiene: la anulación local se limita a `ticket_historial` y `ticket_queue`.
- RF-16: La anulación de tickets fuera de la ventana del vendedor (7 días, RF-11) solo puede realizarse desde el panel de administración/backend.

### 1.4 Identificación de tickets
- RF-17: El `uuid` generado por el front es la identidad permanente del ticket en todo el sistema, incluyendo la base de datos hosteada (MySQL).
- RF-17b: Todo identificador que genere el front es **UUID versión 7**, sin excepciones: el del ticket y el de la sesión de caja. El backend valida la versión al recibirlo y rechaza el evento si no es 7. El motivo es de rendimiento y está en RNF-05.
- RF-18: El backend debe poder distinguir un reintento de envío de una venta nueva usando ese `uuid`, incluso si ambos llegan con el mismo contenido.
- RF-19: El backend debe procesar los eventos de anulación de forma idempotente (reintentos no deben producir efectos duplicados ni inconsistencias).

### 1.5 Gestión de almacenamiento local
- RF-20: `ticket_queue` es transitoria: las filas persisten únicamente hasta que el backend confirma su recepción (`OK`) en la respuesta del lote.
- RF-21: `ticket_historial` conserva las ventas de los últimos 47 días; pasado ese período, se purga localmente sin afectar la capacidad de auditoría en el backend.
- RF-22: SQLite guarda únicamente tickets (`ticket_queue` y `ticket_historial`). No hay tabla de stock ni de productos con cantidades en local.

### 1.6 Stock
- RF-23: El stock es propiedad exclusiva del backend (MySQL). El front nunca lo escribe; lo modifica el backend al procesar los eventos `CREAR` y `ANULAR` del lote.
- RF-24: Estando offline, el front debe poder vender **sin validar disponibilidad de stock**: la validación real ocurre recién cuando el backend procesa el lote.
- RF-25: Si al procesar un evento `CREAR` el stock no alcanza, el backend **no rechaza la venta y no la descuenta parcialmente**. Esto es una caja registradora, no un ecommerce: la venta es física y el vendedor no pudo entregar mercadería que no existía, así que la existencia registrada estaba mal *antes* de la venta. El backend registra primero un movimiento de ajuste por la diferencia —regularización del error de inventario— y después la venta completa. El stock nunca queda negativo, y la suma del kardex sigue coincidiendo con la existencia.
- RF-25b: La venta queda marcada para revisión, pero lo que hay que revisar es **el inventario de ese producto**, no el ticket: el ticket es válido. La corrección se hace con el ajuste contra conteo físico que ya existe.
- RF-25c: Si el ticket se anula después, se repone la cantidad **vendida**, no la que estaba registrada antes: las unidades vuelven físicamente al local.
- RF-26: La pantalla del vendedor no debe mostrar cantidades de stock como dato confiable mientras está offline; si muestra algo, debe ser un valor obtenido del backend con su marca de tiempo, no un cálculo local.

### 1.7 Sesión de caja
- RF-27: La sesión de caja es una entidad más que el front origina: genera su `uuid` (v7) al abrir el turno, y todos los tickets de ese turno lo referencian.
- RF-28: La apertura y el corte del turno viajan como eventos del mismo lote (`ABRIR_SESION` y `CERRAR_SESION`), junto a los tickets. Es lo que permite que un turno que **empezó** durante el corte de internet tenga dónde colgar sus ventas: si la sesión no existe en la nube, el ticket no se puede persistir.
- RF-29: `CERRAR_SESION` lleva el conteo físico del efectivo, el retiro y las observaciones —los datos que el cajero cargó en el local—. El saldo esperado y la diferencia los calcula el backend al procesar el evento, cuando las ventas del turno ya están persistidas.
- RF-30: Ningún movimiento de caja puede imputarse a un turno ya cerrado. Un ticket que llega después del `CERRAR_SESION` de su propio turno se rechaza y queda para revisión del admin: un corte firmado no se mueve. Por el orden de los eventos (RF-09), no debería ocurrir.
- RF-31: Mientras haya una sola caja, el sistema sigue admitiendo **un único turno abierto a la vez**. Dos cajas operando offline en simultáneo (RNF-08) requieren primero decidir el modelo de sesión por caja, que está fuera del alcance de esta etapa.

### 1.8 Fechas
- RF-32: Las fechas las manda el front: la de creación del ticket y la de su anulación. No pueden ser las del `INSERT` en la nube, porque un lote son veinte tickets que ocurrieron en momentos distintos y llegan todos juntos; fechados a la llegada, el resumen diario, los reportes y el arqueo quedan corridos.
- RF-33: El backend registra **aparte** cuándo sincronizó cada ticket. Son dos datos distintos —cuándo se vendió y cuándo llegó— y los dos se conservan.
- RF-34: La fecha de un movimiento de stock o de caja generado por un ticket offline es la del ticket, no la de la sincronización. Si no, el kardex muestra la mercadería saliendo dos días después de la venta que la sacó.
- RF-35: Como la fecha pasa a ser un dato del cliente, el backend la valida: no más de unos minutos en el futuro, no más de 60 días en el pasado, y la anulación nunca anterior al ticket. Un reloj mal configurado no puede envenenar los reportes ni las ventanas de permisos.

### 1.9 Importes
- RF-36: El total del ticket viaja en el evento, pero el backend lo **recalcula** desde los detalles y lo compara. Si la diferencia es de redondeo, guarda el recalculado sin más; si es real, persiste el ticket igual —la venta ya ocurrió— y lo marca para revisión.
- RF-37: El precio unitario de cada línea se toma tal como llega: es el precio que el cliente pagó en ese momento, no el de la lista de hoy.

### 1.10 Contrato de sincronización
- RF-38: El evento `CREAR` trae el `idVendedor`, porque el vendedor es un hecho del momento de la venta y no quien está sincronizando. El backend valida únicamente que ese usuario **exista**: no se exige que esté activo, o una baja durante el corte dejaría sus tickets pendientes sin poder sincronizar nunca.
- RF-39: El backend registra también **quién sincronizó** el evento, tomado del token. Es lo que hace auditable el par vendedor/sincronizador, dado que un empleado podría atribuir un ticket a un compañero.
- RF-40: Un lote admite hasta **100 eventos** por request. El límite no es por tamaño del payload sino por cuánto tiempo el request retiene la base: cada evento es una transacción con locks de fila. Un lote más grande se rechaza con el límite en el mensaje, y el front trocea respetando el orden local.
- RF-41: Para productos con lotes, el backend aplica FEFO al procesar el evento, prefiriendo los lotes que **ya existían a la fecha del ticket**: un lote que ingresó después no pudo haber sido el que salió del estante. Si con esos no alcanza, se aplica RF-25.

---

## 2. Requisitos no funcionales

- RNF-01 (Disponibilidad offline): El sistema debe operar sin internet durante al menos 2 días continuos en lo que respecta a venta y anulación. Las funciones que dependen del stock (consulta de disponibilidad, ajustes, alertas de mínimo) quedan indisponibles offline por decisión de diseño: el stock no se replica en local.
- RNF-02 (Consistencia eventual): Los datos locales y los de la nube deben converger a un estado consistente en cuanto se restablece la conexión, sin intervención manual.
- RNF-03 (Idempotencia): Ninguna operación (creación o anulación) debe poder duplicarse por reintentos de red, cortes a mitad de una petición, o reenvíos automáticos del lote.
- RNF-04 (Rendimiento local): Las consultas del vendedor (ventas recientes, historial de 47 días) deben resolverse en tiempo prácticamente instantáneo, sin depender de la latencia de red.
- RNF-05 (Rendimiento de escritura en MySQL): El `uuid` del front es la clave primaria física, en la columna `BINARY(16)` que ya usan las 16 tablas del sistema, y se genera como **UUID v7**. El problema a evitar es real —un UUID aleatorio como clustered index fragmenta las páginas de InnoDB—, pero v7 lo resuelve en el origen: sus bits altos son el timestamp, así que las inserciones vuelven a ser casi secuenciales. Con eso **no hace falta** el `BIGINT AUTO_INCREMENT` interno que pedía la versión anterior de este requisito, que además asumía un `uuid` en `CHAR(36)` —36 bytes contra 16, replicados en cada índice secundario— y obligaba a reescribir las tres referencias que apuntan a `ventas.id`.
- RNF-06 (Atomicidad local): Las escrituras relacionadas a un mismo evento (creación o anulación) que abarcan varias tablas locales deben ejecutarse dentro de una transacción SQLite única.
- RNF-07 (Uso de almacenamiento local): `ticket_queue` no debe acumular datos más allá de lo pendiente de envío; `ticket_historial` no debe crecer más allá de la ventana de 47 días.
- RNF-07b (Autoridad única del stock): No debe existir ninguna ruta de código que calcule, descuente o repueble stock fuera del backend. El stock tiene una sola fuente de verdad (MySQL), sin réplica que pueda divergir.
- RNF-08 (Escalabilidad multi-caja): El diseño debe soportar múltiples cajas/dispositivos operando offline de forma simultánea sin colisión de `uuid`.
- RNF-09 (Tolerancia a fallos de red parciales): El sistema debe manejar correctamente el caso en que un lote se procesó parcial o totalmente en el backend pero la respuesta nunca llegó al front.
- RNF-10 (Compatibilidad con MySQL): Los mecanismos de idempotencia (constraint único sobre `uuid`, o conflicto de PK) deben implementarse con las capacidades disponibles en MySQL (`UNIQUE KEY` + `INSERT ... ON DUPLICATE KEY UPDATE`, o manejo del error de duplicado).

---

## 3. Arquitectura de tablas locales (SQLite)

| Tabla | Rol | Se lee para... | Se escribe cuando... | Vida útil |
|---|---|---|---|---|
| `ticket_queue` | Cola de envío — eventos `CREAR` y `ANULAR` pendientes de mandar al backend en lote | El worker de sincronización arma el próximo lote | Se crea un ticket, o se anula un ticket (sin importar si el original ya fue enviado) | Hasta que el backend confirma (`OK`) ese evento puntual en la respuesta del lote |
| `ticket_historial` | Historial de consulta — fuente de lectura y de selección para el vendedor (listar, anular) | El vendedor consulta ventas recientes o busca un ticket para anular | Se crea un ticket (en la misma transacción que `ticket_queue`); se anula un ticket (actualización optimista inmediata) | 47 días, luego purga local |

Estas dos tablas son **todo** lo que vive en SQLite. No hay tabla de stock local: ni cantidades, ni lotes, ni movimientos. Lo optimista del esquema aplica al estado del ticket (`ANULADO` se ve al instante), nunca al stock.

**Flujo de anulación (regla clave):** siempre se **lee** de `ticket_historial` y siempre se **escribe** un evento nuevo en `ticket_queue`, nunca se edita o elimina la fila original de la cola. Esto evita una condición de carrera entre el worker de envío por lote (que puede tomar el ticket original justo en el instante de la anulación) y la acción del vendedor.

---

## 4. Qué implican los cambios

| Cambio propuesto | Implicación directa |
|---|---|
| El front origina el ticket y su `uuid`, ya no el backend | El endpoint deja de ser "crear ticket" y pasa a ser "recibir y persistir ticket(s)"; el `uuid` llega en el payload, no se genera en la respuesta. |
| El `uuid` del front es la PK física, en `BINARY(16)`, y se genera como v7 | No se agrega una segunda identidad ni se toca el esquema de las 16 tablas: el `uuid` sigue siendo la identidad estable de cara al front/API y además rinde en InnoDB, porque v7 inserta casi en secuencia. A cambio, el front queda obligado a generar v7 y el backend a validarlo. |
| El ticket declara su sesión de caja, y la sesión también se sincroniza | La caja deja de ser un agujero del diseño: el arqueo cubre lo cobrado offline. A cambio, la apertura y el corte pasan a ser eventos del lote y `CajaApi` tiene que aceptar id y fechas provistos en vez de resolverlos con el reloj del servidor. |
| Las fechas las manda el front | `created_at` deja de significar "cuándo se insertó la fila" y pasa a significar "cuándo ocurrió el ticket". Los reportes y los índices siguen funcionando sin cambios —siempre asumieron eso—, pero hay que sacar `@CreationTimestamp` y fechar explícitamente en todos los caminos, incluido el online, y validar una fecha que ahora es dato del cliente. |
| Ventana de anulación del vendedor de 7 días | Habilita anular una venta ya cobrada, que hoy está prohibido, y obliga a decidir dónde entra la devolución de plata: en el turno abierto de hoy, no en el turno original, que puede tener su corte firmado. |
| Envío por lote en vez de por ticket | El endpoint pasa a aceptar un array de eventos; la respuesta debe ser por ítem, no un único resultado para todo el paquete. |
| Disparador mixto (tiempo O cantidad) estando online | El worker de sincronización necesita ambos temporizador y contador corriendo en paralelo, y disparar el envío con el que se cumpla primero. |
| Errores de validación no se descartan | La cola necesita un estado adicional (ej. `ERROR_PENDIENTE`) distinto de `PENDIENTE` y `SINCRONIZADO`, para diferenciar "aún no enviado" de "se envió y falló, se reintentará". |
| Anulación siempre como evento nuevo en la cola | El backend necesita lógica explícita para dos escenarios: el ticket original ya llegó (aplica la anulación directo) o no ha llegado todavía (debe recordar la anulación pendiente y aplicarla en cuanto llegue el ticket). |
| El stock queda solo en el backend, sin réplica local | El front no valida disponibilidad al vender offline ni muestra stock confiable mientras está sin conexión. En cambio, no hay dos stocks que puedan divergir: el backend aplica descuentos y reposiciones en el orden en que le llegan los eventos, con su lógica de lotes/FEFO y sus locks, que el front no podría reproducir. |
| Venta offline sin validación de stock | Aparece un escenario nuevo para el backend: un evento `CREAR` que pide más unidades de las que hay. No es un error del cliente a rechazar —la mercadería ya salió del local—, sino una discrepancia de inventario a registrar y corregir desde el panel admin. |
| Ventana de `ticket_historial` de 47 días | El vendedor sigue viendo sus ventas del último mes y medio, aunque solo pueda anular las de los últimos 7 días; las anulaciones más viejas se gestionan desde el panel de administración/backend. |

---

## 5. Alternativas evaluadas

### 5.1 Identificador y clave primaria del ticket

| Alternativa | Descripción | Estado |
|---|---|---|
| UUID **v4** como PK física (clustered index) en MySQL | Simplicidad máxima, un solo identificador | Descartada por rendimiento en InnoDB a mayor volumen: aleatorio, fragmenta las páginas |
| `BIGINT AUTO_INCREMENT` interno + `uuid` como `UNIQUE KEY` | El `uuid` sigue siendo la identidad de cara al front/API; la PK física es la que rinde mejor en InnoDB | Descartada — resuelve por esquema lo que el formato del uuid resuelve solo, a cambio de dos identidades, una unique key y reescribir las tres referencias a `ventas.id` |
| UUID **v7** como PK física, en `BINARY(16)` | Los bits altos son el timestamp: las inserciones vuelven a ser casi secuenciales sin agregar una segunda identidad | **Elegida** |
| Clave compuesta (caja + id local + timestamp) | No requiere librería de UUID | Descartada — rígida ante cambios de hardware/dispositivo |
| Rango de folios pre-reservado | El backend entrega un rango de IDs por adelantado | Descartada — bloquea la venta si se agota el rango offline |
| Hash del contenido del ticket | No requiere ID adicional | Descartada — riesgo de colisión y fragilidad ante cambios de formato |

### 5.2 Envío al backend

| Alternativa | Descripción | Estado |
|---|---|---|
| Envío inmediato por ticket | Una request por venta | Descartada — genera ráfagas masivas tras cortes prolongados |
| Envío por lote, disparado por tiempo O cantidad | Agrupa eventos pendientes y los manda juntos, minimizando requests sin demorar demasiado la confirmación | **Elegida** |

### 5.3 Arquitectura de tablas locales

| Alternativa | Descripción | Estado |
|---|---|---|
| Tabla única (`ticket` con estado + sync_status) | Cola e historial mezclados | Descartada — dificulta purgas selectivas |
| Dos tablas (`ticket_queue` + `ticket_historial`) | Cola transitoria + historial de 47 días | **Elegida** |

### 5.4 Propagación de la anulación

| Alternativa | Descripción | Estado |
|---|---|---|
| `UPDATE` directo sobre el ticket original en la cola | Simple, pero falla si el original ya fue purgado | Descartada |
| Evento independiente `ANULAR` en `ticket_queue` | Funciona sin importar el estado del original; evita condición de carrera con el worker de envío | **Elegida** |

### 5.5 Anulación de tickets fuera de la ventana del historial local

| Alternativa | Descripción | Estado |
|---|---|---|
| Permitir desde el front consultando directo al backend | El vendedor anula tickets viejos con consulta puntual online | Descartada |
| Solo desde el panel de administración/backend | Lo histórico se gestiona centralizadamente | **Elegida** |

### 5.6 Manejo del stock offline

| Alternativa | Descripción | Estado |
|---|---|---|
| Réplica de stock en SQLite, descontada y repuesta en local | El vendedor ve disponibilidad al instante incluso offline | Descartada — obliga a duplicar en el front la lógica de lotes/FEFO, movimientos y locks del backend, y crea dos stocks que divergen tras cada corte |
| Stock exclusivo del backend, sin réplica local | El front solo persiste tickets; el stock se mueve cuando el backend procesa el lote | **Elegida** |
| Snapshot de stock descargado periódicamente, solo lectura | Muestra un número orientativo con su marca de tiempo, sin escribirlo nunca | No descartada, pero fuera del alcance de esta etapa |

### 5.7 Sesión de caja durante el corte de internet

| Alternativa | Descripción | Estado |
|---|---|---|
| Los tickets offline entran sin sesión y fuera del arqueo | El admin los concilia contra el efectivo real desde un listado aparte | Descartada — deja el arqueo incompleto justo los días en que más falta hace |
| El ticket declara su sesión, y apertura y corte se sincronizan como eventos | El turno completo —apertura, ventas, corte— viaja en el lote y el backend lo reconstruye en orden | **Elegida** |
| Bloquear la venta hasta que haya conexión para abrir turno | Simplicísimo | Descartada — contradice RNF-01: el local tiene que poder vender |

### 5.8 Fecha del ticket

| Alternativa | Descripción | Estado |
|---|---|---|
| La fecha es la del `INSERT` en la nube | Nada que validar, nada que propagar | Descartada — fecha los veinte tickets del lote en el mismo segundo y corre reportes y arqueo |
| Columna nueva `ocurrido_en` al lado de `created_at` | Separa explícitamente las dos fechas | Descartada — obliga a migrar todos los índices, listados y reportes a la columna nueva |
| `created_at` pasa a ser la fecha del ticket, y se agrega `sincronizado_en` | La columna significa lo que los reportes siempre asumieron; el dato nuevo es el que no existía | **Elegida** |

---

## 6. Decisiones pendientes de definir

Las nueve decisiones de diseño del backend se cerraron el 2026-09-12: identidad y formato del
`uuid` (RNF-05, RF-17b), sesión de caja (sección 1.7), fechas (sección 1.8), ventana y permisos de
anulación (RF-11), tratamiento del total (sección 1.9), stock insuficiente (RF-25), vendedor en el
payload (RF-38), tope de lote (RF-40) y FEFO diferido (RF-41). El fundamento de cada una está en
[`docs/plan-0.6.0-sync-offline-y-despliegue.md`](docs/plan-0.6.0-sync-offline-y-despliegue.md),
sección 2.

**El backend de todo esto está implementado en la 0.6.0.** El contrato del endpoint —forma del
lote, resultado por ítem y códigos de error— está en
[`docs/api-endpoints.md`](docs/api-endpoints.md), sección *Ventas*, y el porqué de cada regla en
[`ARCHITECTURE.md`](ARCHITECTURE.md), sección *Ventas creadas sin conexión*.

Lo que queda abierto es del lado del front, o de negocio:

- **Anulación rechazada por el backend**: si el backend rechaza un evento `ANULAR` (ej. el ticket ya estaba anulado por otra vía), falta definir cómo se corrige el estado del ticket en `ticket_historial`, que ya se marcó como anulado de forma optimista. Ya no hay stock local que revertir, pero el estado mostrado al vendedor sí puede quedar mal.
- **Motivo obligatorio al anular**: si la anulación —del vendedor o del admin— tiene que registrar un motivo. El permiso ya está definido (RF-11).
- **Límite de reintentos antes de escalar un error persistente**: los eventos con error se reintentan en cada ciclo de lote, pero no se definió si hay un número máximo de intentos antes de notificar al admin, o si reintenta indefinidamente. El backend acotó la mitad del problema: cada `ERROR` viene con `reintentable`, y un `false` significa que volver a mandarlo va a fallar exactamente igual. Lo que falta decidir es qué hacer con los `true` que nunca entran —una sesión que no llega nunca, por ejemplo—.
- **Número de ticket legible/correlativo para el comprobante impreso**: quedó sin resolver si, además del `uuid`, se necesita un número secuencial de cara al cliente asignado por el backend.
