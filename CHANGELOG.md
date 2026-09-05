# Changelog

## Sin publicar

Cierre del alta por invitación y limpieza de los límites entre los módulos `auth` y `usuarios`,
más la auditoría de los módulos de catálogo —productos, categorías y proveedores— y de
inventario.

### Cambios que rompen compatibilidad

- **`GET /api/users/v1` cambió de forma.** Acepta `?incluirBajas=` y `UsuarioResponse` trae un
  campo nuevo, `deletedAt`. El comportamiento por defecto no cambia: sin el parámetro devuelve
  solo las cuentas en pie, igual que antes.
- **Se eliminó `POST /api/users/v1`, el alta directa.** La invitación queda como **única**
  forma de dar de alta a alguien: el front tiene que llamar a
  `POST /api/users/v1/invitaciones`, que no lleva contraseña. El alta directa contradecía el
  modelo —quien invita nunca conoce la credencial del invitado— y dejaba dos caminos para lo
  mismo. Se fueron con él `UsuarioApi.crear` y el DTO `CrearUsuarioRequest`.
- **Se eliminó `POST /api/auth/v1/verify-email`.** El circuito de verificación de email había
  quedado sin emisor cuando se sacó el autorregistro: nadie llamaba a
  `generateVerificationToken`, así que el endpoint validaba tokens que el sistema nunca creaba.
  Si el front todavía lo invoca, ahora recibe `404`.
- **`PUT /api/productos/v1/{id}` pasa a ser un reemplazo total.** Los campos opcionales que se
  omitan o vengan en `null` ahora **se borran**. Antes se salteaban, y eso hacía imposible
  desasignar la categoría, el proveedor, el costo o el margen: el pedido se aceptaba con `200` y
  el valor viejo quedaba intacto. Un formulario que hoy manda solo lo que cambió tiene que pasar
  a mandar la representación completa.
- **`DELETE /api/productos/v1/{id}` y `DELETE /api/categorias/v1/{id}` pueden responder `400`.**
  El producto se rechaza si todavía tiene existencias —stock o lotes con unidades— y la
  categoría si tiene productos asignados. Antes las dos bajas se llevaban puesto ese dato en
  silencio.
- **`GET /api/proveedores/v1` cambió de forma.** Acepta `?incluirBajas=` y `ProveedorResponse`
  trae `deletedAt`. Sin el parámetro devuelve solo los proveedores en pie, igual que antes.
- **`GET /api/inventario/v1/movimientos/{idProducto}` devuelve un `Page`.** Era una lista sin
  techo sobre la única tabla del módulo que crece con cada venta y cada compra. Acepta
  `?page=` y `?size=` (1 a 100) y ordena del movimiento más reciente al más viejo.
- **`PUT /api/inventario/v1/stock/aumentar` y `/disminuir` solo aceptan `tipo` `AJUSTE` o
  `MERMA`.** `COMPRA` y `VENTA` los escribe el sistema al registrar el comprobante. Ver
  *Seguridad*.
- **`POST /api/inventario/v1/controlar` y `DELETE /api/inventario/v1/stock/{idProducto}` pasaron
  a ser solo de ADMIN.** Un `EMPLEADO` que los use recibe `403`.
- **Una compra con productos que manejan lotes exige la fecha de vencimiento de cada línea.**
  El alta del lote pasó a hacerse por el módulo de inventario, que ya la exigía; la compra lo
  creaba a mano y se salteaba esa validación, así que podía dejar lotes en `SIN_FECHA`, invisibles
  para el control de vencimientos.
- **Un proveedor no puede repetir número dentro del mismo tipo de comprobante.** Cargar dos
  veces el mismo remito duplicaba el ingreso de stock y la salida de caja sin dejar señal. La
  regla es por proveedor y por tipo: dos proveedores distintos pueden emitir el mismo número, y
  un mismo proveedor puede tener un remito y una factura con el mismo número porque cada tipo
  lleva su propia numeración. Ver *Base de datos*.
- **`GET /api/caja/v1/movimientos` devuelve un `Page` y exige las dos fechas o ninguna.**
  Acepta `?page=` y `?size=` (1 a 100). Antes, con solo `hasta`, el rango arrancaba en el año
  2000 y se traía sin paginar la tabla que suma una fila por cada venta cobrada en efectivo,
  cada compra pagada por caja y cada reversa.
- **Los tres listados de ventas devuelven un `Page`.** `GET /api/ventas/v1`,
  `/api/ventas/v1/usuario/{idUsuario}` y `/api/ventas/v1/fecha` aceptan `?page=` y `?size=`
  (1 a 100) y ya no devuelven una lista. El primero traía **todas** las ventas de la historia
  del comercio con todos sus detalles en cada request, sobre la tabla que más rápido crece del
  sistema.
- **Un `EMPLEADO` solo ve sus propias ventas.** Vale para los tres listados y para
  `GET /api/ventas/v1/{id}`, que responde `403` con la venta de otro. `GET /api/ventas/v1`
  devuelve ahora cosas distintas según quién llame: todas para un `ADMIN`, las propias para el
  resto. Antes cualquier autenticado veía las de todos y podía listar las de un compañero por
  id de usuario.
- **`montoRecibido` dejó de ser obligatorio al cobrar, salvo en efectivo.** Con `TARJETA` o
  `TRANSFERENCIA` se ignora y la venta queda con el campo en `null`: antes había que mandar un
  número —normalmente el total exacto— para que el cobro pasara, y ese número quedaba guardado
  como si fuera la plata que entregó el cliente. En efectivo sigue siendo obligatorio y tiene
  que alcanzar para el total.
- **`DELETE /api/compras/v1/{id}` ya no lleva el header `idUsuario`.** Era el último endpoint que
  lo exigía, contra lo que la documentación viene diciendo desde hace dos versiones. Si el front
  lo sigue mandando, se ignora; si antes lo omitía, dejaba de responder `500`.

### Agregado

- **`POST /api/users/v1/{id}/restaurar` — reactivación de una cuenta dada de baja.** Vuelve
  como `PENDIENTE`, con la contraseña anterior invalidada y una invitación nueva, así la persona
  define otra credencial y de paso confirma que sigue teniendo ese mail. Restaura la fila
  original en lugar de crear una cuenta nueva: el id lo referencian `ventas`, `compras`,
  `movimientos_caja`, `movimientos_stock` y `sesiones_caja` con `ON DELETE RESTRICT`, así que un
  alta nueva con el mismo email partiría el historial de esa persona en dos. Conserva su rol y
  rige la jerarquía de siempre. Si el mail no sale, la restauración se revierte.
- **`GET /api/users/v1?incluirBajas=true`** para listar también las cuentas dadas de baja, que
  en el listado normal son invisibles: sin esto el administrador no tiene de dónde sacar el id
  para restaurarlas. **`UsuarioResponse` suma `deletedAt`**, null salvo en ese listado.
- **El `400` de un email ya tomado ahora dice qué hacer**, según el estado de la cuenta que lo
  ocupa: dada de baja → restaurala; `PENDIENTE` → reenviale la invitación en vez de invitarla de
  nuevo; en pie → la persona ya está en el sistema. Antes los tres casos compartían
  `"El email ya está registrado"`, y el administrador quedaba sin salida en los dos primeros.
  La acción sugerida aparece **solo si la cuenta está por debajo del nivel de quien invita**,
  que es lo que exigen restaurar y reenviar: a un ADMIN que tropieza con la cuenta de otro
  ADMIN se le informa el hecho, pero no se le propone algo que le daría `403`.
- **El invitado elige su nombre de usuario.** `POST /api/auth/v1/invitacion/aceptar` acepta un
  campo `username` opcional (1-50 caracteres, sin `@`). Si no viene, queda el que se derivó del
  email al invitarlo, como hasta ahora. Un nombre ya tomado responde `400` y no se desambigua
  con un sufijo: lo está eligiendo a mano y tiene que enterarse.
- **`GET /api/auth/v1/invitacion?token=…`**, público, para que el formulario de aceptación
  salude a la persona y le precargue el `usernameSugerido`. **No consume el token**, así que
  sirve además para detectar un enlace vencido antes de hacerle llenar el formulario.
- **Documentación OpenAPI del módulo `auth`**: los siete endpoints de `/api/auth` salen en
  `/swagger-ui.html` con resumen, descripción y los códigos de error que devuelve cada uno. El
  resto de los controllers sigue con la documentación autogenerada, sin anotar.
- **`POST /api/proveedores/v1/{id}/restaurar` — reactivación de un proveedor dado de baja.**
  Vuelve sobre la fila original, no sobre un alta nueva: las compras la referencian por id, así
  que un proveedor nuevo con los mismos datos partiría el historial en dos. Los dados de baja se
  listan con `GET /api/proveedores/v1?incluirBajas=true`. Se rechaza si el proveedor está en pie
  o si mientras tanto se dio de alta otro activo con el mismo nombre.
- **`409` para los cruces de concurrencia sobre el inventario.** Espera de lock agotada o
  deadlock resuelto por la base dejan de salir como `500`: el pedido estaba bien y se puede
  reintentar tal cual.
- **El historial de movimientos de stock expone `idLote`, `idReferencia` e `idUsuario`.** Sin
  esos tres no alcanzaba para auditar: no se podía saber de qué lote salió cada unidad, qué
  comprobante lo originó ni quién lo cargó.

### Cambiado

- **`auth` deja de tocar las tablas de `usuarios`.** Accedía al `UsuarioRepository` y a la
  entidad `Usuario` directamente: cambiaba el estado de las cuentas, escribía contraseñas y
  duplicaba el armado de `UsuarioResponse`. Ahora todo pasa por `UsuarioApi`, que expone solo
  DTOs. En el código: `AuthService` ya no tiene un `PasswordEncoder` —el formato del hash es
  asunto de quien guarda la credencial— y `UsuarioApi` suma `verificarCredenciales`,
  `buscarPorIdentificador`, `getCuentaInvitada`, `establecerPasswordInicial` y
  `restablecerPassword`. Nada de esto cambia la API HTTP.
- **`POST /api/auth/v1/refresh` rechaza a los usuarios dados de baja.** Buscaba la cuenta sin
  mirar `deleted_at`, así que alguien eliminado podía seguir renovando su sesión mientras le
  durara el refresh token.
- **El login responde con un único mensaje de error.** Antes distinguía "credenciales inválidas
  o cuenta sin acceso" de "credenciales inválidas"; esa diferencia dejaba deducir qué cuentas
  existen y en qué estado están.
- **El inventario se escribe con la fila bloqueada.** Aumentar, disminuir, el ajuste manual, el
  descuento FIFO de una venta y las reversas de anulación leen la fila de stock o de lote con
  `SELECT ... FOR UPDATE`. Antes leían y reescribían sin bloqueo: dos ventas simultáneas del
  mismo producto partían de la misma cantidad y una pisaba a la otra, con lo que se vendía de
  más y el faltante no quedaba registrado en ningún lado. Los locks se toman siempre en el mismo
  orden —productos por id ascendente y, dentro de cada uno, sus lotes por vencimiento—, así que
  dos operaciones no pueden trabarse cruzadas. El detalle de una venta o una compra se sigue
  guardando y devolviendo en el orden en que se cargó.
- **Dar de baja un producto limpia su stock y sus lotes.** Quedaban activos y seguían sumando en
  los reportes de existencias, que leen esas tablas sin pasar por el catálogo.
- **Dar de baja un proveedor ya no lo borra del historial.** Deja de poder usarse en compras
  nuevas y de asignarse a un producto, pero las compras ya registradas y los productos que lo
  tenían lo siguen mostrando, con `deletedAt` cargado. Antes el historial pasaba a mostrar
  `proveedor: null`, o sea que una baja borraba a quién se le había comprado.
- **El nombre de una categoría dada de baja vuelve a estar disponible.** Ver *Base de datos*.
- **El ajuste manual de stock crea la fila si falta y rechaza los productos con lotes.** Un
  producto sin fila de stock es stock 0 y no un error, igual que en la lectura; antes el
  operador veía 0 y el ajuste sobre ese mismo producto respondía `404`. Y ajustar por stock un
  producto que lleva lotes escribía una fila que el cálculo de existencias después ignora: el
  ajuste quedaba registrado y no cambiaba nada de lo que ve el usuario.

### Eliminado

- El flujo de verificación de email, entero: el endpoint, `AuthApi.verifyEmail`,
  `TokenService.generateVerificationToken`, el valor `VERIFICATION` de `TokenType`,
  `UsuarioApi.activarCuenta` y los DTO `VerifyEmailRequest` y `RegisterRequest` —este último ya
  no lo referenciaba nadie desde que el alta pasó al módulo de usuarios.
- `AuthApi.crear`, que solo delegaba en `UsuarioApi.crear` y ningún controller exponía.
- `UsuarioApi.crear`, `UsuarioService.crear` y `CrearUsuarioRequest`, con el endpoint de alta
  directa que los exponía.

### Correcciones

- **Un invitado que reseteaba la contraseña en vez de aceptar la invitación quedaba afuera para
  siempre.** `POST /api/auth/v1/password-reset` alcanza a las cuentas `PENDIENTE`, pero
  confirmar el reseteo cambiaba el hash sin tocar el estado, y el login exige una cuenta activa:
  la persona terminaba con una contraseña válida y un `401` inexplicable. Ahora el reseteo
  **activa la cuenta pendiente** —el token viajó al email de la cuenta, la misma prueba de
  identidad que pide la invitación—. Una cuenta bloqueada no se destraba por ese camino.
- **La invitación deja de valer cuando la cuenta ya se activó.** Es la contracara del punto
  anterior: si no, el enlace del mail seguiría sirviendo por las horas que le quedaran para
  cambiarle la contraseña a una cuenta activa sin conocer la actual. `getCuentaInvitada` y
  `establecerPasswordInicial` ahora exigen estado `PENDIENTE`, no solo que la cuenta no esté
  bloqueada ni dada de baja.
- **Invitar con el email de una cuenta dada de baja devolvía un `409` incomprensible.** La
  validación del alta miraba `deleted_at` y las unique keys `uk_usuarios_email` /
  `uk_usuarios_username` no: el alta pasaba la validación y reventaba en el INSERT con
  `"La operación choca con una restricción de datos existente"`. Ahora se rechaza antes, con
  `400` y un mensaje que lo explica (`"El email pertenece a una cuenta dada de baja"`). El
  username derivado del email también cuenta las bajas lógicas al desambiguar con sufijo, así
  que ya no puede proponer uno ocupado por una cuenta borrada.
- **La aplicación no arrancaba.** `AuthService` y `UsuarioService` quedaron dependiendo uno del
  otro al pasar auth a consumir `UsuarioApi`, y Spring rechaza las referencias circulares desde
  Boot 2.6: el contexto moría con `BeanCurrentlyInCreationException`. Se corta con `@Lazy` sobre
  el `AuthApi` de `UsuarioService` —el lado que solo se usa para efectos posteriores al alta o a
  la baja— más un `lombok.config` que le permite a `@RequiredArgsConstructor` copiar la
  anotación al constructor que genera, sin el cual el `@Lazy` sería inerte.
- **Swagger no levantaba.** `springdoc-openapi` estaba en `2.5.0`, de la serie que acompaña a
  Spring Boot 3.2, contra el `3.5.13` del proyecto: el arranque cortaba con `NoSuchMethodError`
  en `ControllerAdviceBean`. Actualizado a `2.8.9`.
- **Un producto dado de baja seguía siendo editable.** El `update` lo buscaba sin mirar
  `deleted_at`: respondía `200` y hasta dejaba moverle el código de barras encima del de un
  producto activo. Ahora es `404`.
- **Varios errores del cliente salían como `500`.** Un `page` negativo o un `size` fuera de rango
  reventaban dentro de `PageRequest.of`; el alta de producto con un usuario inexistente lanzaba
  un `RuntimeException` pelado; y la validación de los parámetros de consulta no tenía handler,
  así que caía en el catch-all. Todos responden `400` con su mensaje.
- **La paginación del catálogo repetía y salteaba filas.** Ordenaba solo por `updatedAt`, que se
  mueve solo porque cada compra reescribe el costo y el precio del producto, y que no desempata
  entre productos cargados juntos. Se agregó el `id` como segundo criterio.
- **Un producto sin nombre tiraba abajo los cinco endpoints de lotes.** El mapa de nombres se
  armaba con `Collectors.toMap`, que no admite valores nulos, y la columna sí los admite.
- **El listado de lotes por estado barría todo.** Traía el catálogo entero y todos los lotes para
  descartar en memoria; ahora cada estado es un rango de fechas resuelto en una consulta.
- **La fila de stock se podía borrar con existencias**, que era además la forma de saltearse la
  validación equivalente de la baja de producto.
- **El listado de compras contaba de más en el borde del rango.** El filtro por fechas era
  inclusivo en `hasta`, pero el reporte de ganancias le pasa el día siguiente a medianoche
  esperando un rango semiabierto —que es como está escrito el otro query del mismo repositorio y
  como funciona ventas—. Una compra registrada exactamente a las `00:00:00.000000` del día
  siguiente entraba en el reporte del día anterior.
- **El corte de caja se archivaba con la fecha equivocada si el turno cruzaba la medianoche.**
  Un turno que abre a las 22:00 y cierra a las 02:00 quedaba fechado al día siguiente, y el
  corte es un documento contable: esa fecha queda guardada. Lo mismo pasaba en el resumen que
  el cajero mira antes de cerrar. Ahora las dos salen de la apertura del turno.
- **Dos cortes simultáneos se pisaban.** El cierre leía el turno, calculaba el arqueo y lo
  escribía sin bloquear la fila, así que dos cierres a la vez —dos terminales, o un doble
  clic— lo cerraban los dos: el segundo pisaba el saldo real, la diferencia y el desglose del
  primero, que ya le había devuelto al usuario un corte que no quedó guardado. El índice único
  de sesión abierta no alcanzaba, porque cerrar libera ese lugar en vez de ocuparlo.
- **El listado de movimientos de caja no validaba el rango**: invertido devolvía vacío en
  silencio.
- **El resumen de ventas de un turno se fechaba con el día en que se lo consultaba.**
  `GET /api/ventas/v1/resumen/sesion/{idSesion}` usaba la fecha de hoy en lugar de la del turno,
  así que un turno que abre a las 22:00 y cierra a las 02:00 salía fechado al día siguiente, y
  consultar hoy el turno de ayer devolvía la fecha equivocada. Es el endpoint que se mira al
  cerrar caja. Ahora sale de la apertura de la sesión.
- **Un rango de fechas invertido devolvía un listado vacío en silencio**, y quien preguntaba se
  quedaba pensando que no hubo ventas en vez de que se equivocó de fechas. Ahora es `400`. La
  amplitud del rango no se acota: los listados paginan, así que un rango grande no trae más
  filas por request.
- **Faltar un query param obligatorio respondía `500`.** `GET /api/ventas/v1/fecha` sin fechas
  caía en el catch-all; ahora es `400` y dice cuál falta. Alcanza a todos los endpoints con
  parámetros obligatorios.
- **El listado de ventas levantaba también las anuladas para descartarlas en memoria**, con un
  `findAll()` que ignoraba el índice de `deleted_at`.
- **`tipoComprobante` se guardaba tal cual venía y el filtro comparaba por igualdad exacta**, así
  que `"factura"`, `"Factura"` y `"FACTURA "` eran tres tipos distintos: una compra cargada con
  uno no aparecía al filtrar por otro. Ahora se recorta y se guarda en mayúsculas, y la búsqueda
  usa el mismo criterio.
- **Anular una compra cuya mercadería ya se vendió explicaba mal el problema** cuando el producto
  no manejaba lotes: salía "Stock insuficiente. Disponible: 3, solicitado: 10", correcto pero
  desorientador. Ahora dice que ya se vendió parte de lo que ingresó esa compra y nombra el
  producto, igual que la rama de lotes.
- **La paginación de compras repetía y salteaba filas**, y un `page` o un `size` fuera de rango
  salían `500`. Ordenaba solo por fecha o solo por importe, sin desempate: las compras cargadas
  en el mismo lote comparten `createdAt` y por importe la colisión es todavía más probable.

### Seguridad

- **El módulo de inventario estaba entero abierto a cualquier usuario autenticado.** No tenía
  ninguna regla propia en `SecurityConfig`. El ajuste manual de stock y la baja de la fila de
  stock —las dos operaciones que pueden tapar un faltante sin dejar rastro operativo— pasaron a
  ADMIN, junto con el resto de lo sensible. Aumentar y disminuir siguen abiertos: son el
  movimiento normal del día.
- **Quien anulaba una compra elegía con qué identidad quedaba registrado.** El `idUsuario` de
  `DELETE /api/compras/v1/{id}` salía de un header que mandaba el cliente, así que se podía
  firmar la reversa de stock y la entrada de caja con el id de otro usuario: justo las dos
  tablas con las que después se audita quién tocó qué. Ahora sale del JWT, como en la anulación
  de ventas y como el resto del sistema.
- **Un movimiento cargado a mano podía hacerse pasar por el de una venta.** El endpoint aceptaba
  `tipo` e `idReferencia` del cliente, y son justo los dos campos que lee la reversa de una
  anulación para saber qué reponer. Ahora el tipo se limita a `AJUSTE` o `MERMA`, la referencia
  del body se descarta y el usuario sale siempre del JWT.

### Rendimiento

- **El catálogo resolvía la categoría y el proveedor de cada producto fila por fila.** Una página
  de 20 eran 41 consultas, y el reporte de inventario —que pide el catálogo entero— más de 200,
  lo que anulaba el trabajo de las consultas agregadas de existencias. Ahora se resuelve una vez
  por id distinto.
- **Los listados de categorías y proveedores traían la tabla entera y filtraban en memoria**,
  incluidas las filas dadas de baja, ignorando el índice de `deleted_at`.
- **El tamaño de página del catálogo tiene techo** (100). Sin límite, un solo pedido podía exigir
  un trabajo arbitrariamente grande.

### Configuración

- **`ventas.reserva-stock.minutos`** (default 120) — cuánto tiempo una venta sin cobrar retiene
  el stock que descontó al armarse. El descuento ocurre al armar la venta y no al cobrarla, así
  que un ticket que nadie cerró dejaba mercadería reservada para siempre, invisible en el stock
  disponible y sin nada que lo mostrara. Pasado ese plazo un barrido la anula y devuelve la
  mercadería, con su movimiento de reversa. En **`0`** queda apagado, que es el comportamiento
  anterior.
- **`ventas.reserva-stock.revision-ms`** (default 300000) — cada cuánto corre ese barrido.

### Base de datos

- **`04_quitar_verification.sql` — aplicar ANTES de desplegar esta versión.** Borra los tokens
  históricos de tipo `VERIFICATION` y saca ese valor del enum `auth_tokens.token_type`. El enum
  de Java ya no lo conoce, así que una fila con ese valor falla al leerse. Si la aplicación
  arranca antes de la migración, cualquier consulta que toque una de esas filas revienta.
- `00_init_limpio.sql` ya crea `token_type` como `ENUM('PASSWORD_RESET','INVITATION')`: una
  instalación nueva no necesita la migración.
- **`05_unicidad_barcode.sql` — unicidad de `barcode` entre los productos activos.** El chequeo
  vivía solo en el servicio, entre un `SELECT` y un `INSERT`: dos altas simultáneas con el mismo
  código pasaban las dos, y desde ahí la consulta por código devolvía más de un resultado y
  respondía `500` de forma permanente. El índice va sobre una columna generada que vale el código
  mientras la fila está activa y `NULL` cuando está borrada, así que los productos dados de baja
  no reservan el código.
- **`06_nombre_categoria_reutilizable.sql` — el nombre de una categoría borrada se libera.**
  `uk_categorias_nombre` abarcaba también las filas borradas, mientras que el servicio validaba
  filtrando por `deleted_at`: daba el nombre por libre y el `INSERT` chocaba con un `409`
  inentendible, sin forma de recrear la categoría nunca más. Se reemplaza por el mismo patrón de
  columna generada.
- **`07_unicidad_comprobante_por_proveedor.sql` — un proveedor no repite comprobante.** Índice
  único sobre `(id_proveedor, tipo_comprobante, nro_comprobante)` entre las compras activas, con
  el mismo patrón de columna generada. Quedan fuera, a propósito, las compras sin proveedor, las
  que no traen número y las anuladas: anular libera el número para volver a cargar la compra
  bien. El tipo sin cargar cuenta como un valor más, así que no abre un agujero por el que se
  cuelen duplicados.
- Las tres migraciones abren con una consulta informativa de los datos que podrían frenar el
  `ALTER`. Aplicar en orden y con la aplicación detenida. `00_init.sql` y `00_init_limpio.sql` ya
  traen los tres índices: una instalación nueva no necesita las migraciones.

## 0.3.0 (2026-08-21)

Primeros puntos de [`docs/cambios.md`](docs/cambios.md). El sistema se despliega **una instancia
por comercio**, así que no hay multi-inquilino: el aislamiento entre comercios lo da el
despliegue, no el modelo de datos.

### Cambios que rompen compatibilidad

- **`UsuarioResponse` reemplaza `enabled` (booleano) por `estado`** (`PENDIENTE` | `ACTIVO` |
  `BLOQUEADO`). El front tiene que dejar de leer `enabled`.

### Agregado

- **Rol `SUPERADMIN`**, dueño del sistema, en jerarquía `SUPERADMIN > ADMIN > EMPLEADO`. Cada
  rol puede todo lo del rol de abajo y gestiona (alta, bloqueo, baja) a los de nivel inferior.
  La jerarquía es estricta: un ADMIN no puede dar de alta, bloquear ni eliminar a otro ADMIN, y
  al SUPERADMIN no lo toca nadie. Tampoco se puede uno bloquear o borrar a sí mismo.
  - No hay alta de SUPERADMIN por API: sale del seed de la base. Si fuera un endpoint,
    apoderarse de una sesión de superadmin alcanzaría para fabricarse otro.
  - Las reglas por URL no cambiaron: el filtro JWT publica las authorities del rol **y las de
    los roles inferiores**, así que los `hasRole('ADMIN')` existentes ya incluyen al SUPERADMIN.
- **Alta por invitación con SMTP**, el flujo que pedía `docs/cambios.md`:
  - `POST /api/users/v1/invitaciones` — el administrador carga nombre, apellido, email y rol; la
    cuenta nace `PENDIENTE` con una contraseña aleatoria que nadie conoce, y a la persona le
    llega un mail para definir la suya. `username` es opcional: si no viene se deriva del email.
  - `POST /api/auth/v1/invitacion/aceptar` — público, cierra el alta: define la contraseña y
    activa la cuenta. El enlace dura 72 h y sirve una sola vez.
  - `POST /api/users/v1/{id}/invitaciones/reenviar` — token nuevo, el anterior se invalida.
  - Si el envío falla, el alta se revierte y responde `502`: no queda una cuenta muerta ocupando
    ese email y ese username que nadie puede activar.
- **Reseteo de contraseña por email**: `POST /api/auth/v1/password-reset` ahora manda el mail.
  Sigue respondiendo `200` exista o no la cuenta — y también si el SMTP falla, porque un `502`
  solo para las cuentas que existen revelaría cuáles existen.
- **Configuración de SMTP** (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
  `MAIL_FROM`, `MAIL_FROM_NAME`) y `FRONTEND_URL` para armar los enlaces de los mails.
  **Sin `MAIL_HOST` la app funciona igual**: deja el mail en el log, con el enlace incluido.
- **Cambio de rol**: `PATCH /api/users/v1/{id}/rol`. Exige jerarquía por partida doble —el
  objetivo y el rol nuevo tienen que estar por debajo de quien lo pide—, así que en la práctica
  solo el SUPERADMIN mueve gente entre ADMIN y EMPLEADO, y `SUPERADMIN` nunca es asignable.
  Nadie se cambia el rol a sí mismo. Corta las sesiones del usuario para que reciba un token
  con el rol nuevo.
- **Bloqueo de usuarios**, reversible y sin borrar la cuenta:
  `POST /api/users/v1/{id}/bloquear` y `/desbloquear`, para ADMIN y SUPERADMIN según la
  jerarquía. El usuario bloqueado conserva su historial y sus sesiones se cortan en el acto.
- **Login indistinto con email o username.** Gana el email, que es la credencial principal.
  También vale para pedir el reseteo de contraseña.
- **`username` es único.** Se valida en el alta (`400` si está en uso) y en la base. No puede
  contener `@`: un username con forma de email haría ambiguo el login.

### Cambiado

- **El rol se lee de la base en cada request**, no del claim del JWT. El claim queda viejo
  apenas cambia el rol del usuario, y un ADMIN degradado habría seguido mandando hasta que su
  token expirara (24 h por defecto). El claim sigue viajando en el token, ahora como dato
  informativo para el front. En el código: `UsuarioApi.puedeOperar(id)` pasa a ser
  `rolVigente(id)`, que devuelve el rol o vacío si el usuario no puede operar.
- `DELETE /api/users/v1/{id}` ahora rechaza la baja de la cuenta propia y la de usuarios que no
  estén por debajo del que la pide. Antes cualquier ADMIN podía borrar a cualquiera, incluido él
  mismo, y dejar el comercio sin administrador.

- El flag `enabled` pasa a la columna `estado`. El booleano solo distinguía "verificado" de "no
  verificado" y no dejaba lugar para el bloqueo: suspender a alguien obligaba a borrarlo,
  perdiendo la diferencia entre "se fue" y "no puede entrar por ahora".
- El login exige estado `ACTIVO`. El mensaje de error no distingue entre credenciales inválidas,
  cuenta pendiente y cuenta bloqueada, para no revelar qué cuentas existen.

### Base de datos

- `02_parche_migraciones.sql` consolida las migraciones de las fases 3 a 5 y los cambios de
  usuarios, SUPERADMIN e invitaciones. Aplicarlo una sola vez sobre una base existente que aún
  no tenga esas modificaciones. **Verificar antes que no haya usernames repetidos**: el parche
  incluye la consulta y explica cómo seguir si el índice falla.
- Para una instalación nueva usar `00_init_limpio.sql` y, opcionalmente, `01_seed.sql`. El init
  contiene el esquema final de esta versión y no requiere ejecutar migraciones adicionales.

### Pendiente de `docs/cambios.md`

- Permisos configurables por empleado — descartado por ahora: todas las funciones activas.

Con esto queda cubierto todo `docs/cambios.md` salvo ese último punto.

## 0.2.0 (2026-08-18)

Relevamiento y corrección de 25 bugs del MVP. Todos los defectos fueron reproducidos contra la
API real antes de corregirlos, y cada corrección verificada del mismo modo. El detalle completo
—causa, solución y evidencia de cada uno— está en [`docs/plan-correccion-bugs.md`](docs/plan-correccion-bugs.md).

### Cambios que rompen compatibilidad

Requieren cambios en el front:

- **`POST /api/auth/v1/logout`** recibe el refresh token en el **body** (`{"refreshToken": "..."}`),
  ya no el access token por header. Antes fallaba siempre con `400` y la sesión seguía viva.
- **`POST /api/auth/v1/register` fue dado de baja.** El alta de usuarios pasa a
  `POST /api/users/v1`, restringida a ADMIN. La lógica de autorregistro queda implementada pero
  sin endpoint, a la espera del envío de mails.
- **Se eliminó el header `idUsuario`** de todos los endpoints. La identidad sale del JWT; si se
  envía el header, se ignora.
- **`401` y `403` ahora son distintos:** `401` = token ausente, inválido, expirado o de un
  usuario dado de baja (reautenticar); `403` = autenticado pero sin permisos.
- **`POST /api/ventas/v1` ya no acepta `idSesion`.** La sesión de caja se resuelve al cobrar.
- **`POST /api/compras/v1`:** `idSesion` se reemplaza por `pagoEnEfectivo` (booleano).
- **`cambio` es 0** en pagos con tarjeta y transferencia.
- **`GET /api/caja/v1/resumen/diario` cambió de semántica:** ahora resume la fecha pedida.
  Para el turno abierto se agregó `GET /api/caja/v1/resumen/sesion`.
- **`GET /api/reportes/v1/ganancias` cambió de forma:** suma `costoMercaderiaVendida` y
  `unidadesSinCosto`, y `totalCompras` deja de restarse.
- **`DELETE` de ventas y compras puede responder `400`** con el motivo, y ya no es un borrado
  silencioso.
- Los totales de `ResumenCaja` pueden venir **`null`** en cortes anteriores a esta versión
  (dato desconocido, distinto de 0).

### Seguridad

- El logout revoca la sesión de verdad y es idempotente.
- El reseteo de contraseña funciona con más de una sesión activa: antes fallaba con `500` y
  revertía el cambio en silencio, dejando la contraseña vieja.
- La identidad de toda operación se toma del JWT, no de un header que el cliente controla.
  Antes cualquier usuario podía registrar ventas a nombre de otro.
- Los roles ADMIN y EMPLEADO se aplican de verdad (`@EnableMethodSecurity` + matriz de
  permisos). Antes el claim `rol` se leía y se descartaba: un empleado podía borrar al admin.
- Dar de baja un usuario revoca sus sesiones y su JWT deja de servir en el acto.
- `jwt.secret` es obligatorio: sin él la app no arranca. Antes generaba un secreto aleatorio por
  arranque, invalidando todas las sesiones en cada reinicio.
- Cambiar la contraseña exige la actual y solo puede hacerlo el dueño de la cuenta.

### Correcciones de negocio

- **Anular una venta o compra revierte el stock**, reponiendo cada lote en la cantidad exacta de
  la que salió, incluso cuando el FIFO repartió una línea entre varios lotes. Antes el stock
  quedaba descontado para siempre.
- **Solo el efectivo impacta en la caja.** Las ventas con tarjeta o transferencia quedan
  registradas con su medio de pago pero fuera del arqueo, que cuenta billetes.
- **No se puede cobrar contra un turno de caja ya cerrado.** Antes la plata entraba a una sesión
  con el corte firmado y no aparecía en ningún lado.
- **No se puede anular una venta cobrada** (corresponde devolución), ni una compra cuya
  mercadería ya se vendió, ni una pagada en un turno que ya cerró.
- Una sola sesión de caja abierta a la vez, garantizado también a nivel base de datos.
- El resumen de caja respeta la fecha pedida y no exige que haya una caja abierta.
- El corte guarda su propio desglose: el historial dejó de devolver todo en cero.
- El reporte de inventario cuenta los lotes: los productos con lote ya no aparecen en 0.
- La ganancia es margen real (`ventas − costo de lo vendido`) sobre ventas cobradas. Antes
  restaba las compras del período, así que una reposición grande daba pérdida.
- Los reportes de ventas, ganancias y resumen diario devuelven el mismo total para el mismo
  rango, filtrando por fecha de cobro.
- Los rangos de fecha dejaron de solaparse: una venta a las 00:00:00 se contaba en dos días.
- Validación de cantidades y montos mayores a cero en ventas, compras, stock y caja.
- Un producto no puede tener dos filas de stock; consultar el stock de un producto sin
  movimientos devuelve 0 en vez de fallar.

### API y manejo de errores

- Ruta inexistente ⇒ `404`, UUID o parámetro mal formado ⇒ `400`, JSON ilegible ⇒ `400`,
  violación de integridad ⇒ `409`. Todos respondían `500`.
- Los errores internos quedan registrados con stacktrace: antes desaparecían sin rastro.
- `POST` de ventas, compras y movimientos de caja devuelven la fecha de creación, que llegaba
  en `null`.
- Nuevo `GET /api/ventas/v1/resumen/sesion/{idSesion}`: desglose por medio de pago de un turno,
  complemento del corte.
- **CORS habilita `PATCH`**, que faltaba en la lista de métodos permitidos: el preflight del
  navegador rechazaba `PATCH /api/users/v1/{id}` y el endpoint era inalcanzable desde el front.

### Base de datos

El parche acumulado `02_parche_migraciones.sql` reúne los cambios de las fases 3, 4 y 5:
`movimientos_stock.id_referencia`, restricciones de stock y caja, desglose persistido del corte,
`detalles_ventas.costo_unitario`, índice por fecha de cobro y el cambio de `refresh_token` a
`refresh_tokens`. **Aplicar con la app detenida:** invalida las sesiones abiertas.

Para una base nueva alcanza con `00_init_limpio.sql` + `01_seed.sql`. El esquema valida contra las
entidades con `spring.jpa.hibernate.ddl-auto=validate`.

### Rendimiento

- Los listados de ventas y compras usan dos consultas fijas en vez de una por comprobante.
- El reporte de ventas resuelve el rango completo de una vez: un reporte de 30 días pasó de
  más de 30 consultas a 3.
- El reporte de inventario resuelve las existencias con dos consultas agregadas.
- `GET /api/inventario/v1/lotes` dejó de escribir en la base: el estado del lote se calcula al
  leer.

### Deuda conocida

- **No hay tests automatizados** más allá de la carga de contexto. Toda la verificación de esta
  versión fue manual.
- Los listados de ventas y compras no están paginados: traen la tabla entera.
- Las migraciones se aplican a mano; falta incorporar Flyway.
- Los importes usan `float`. Para dinero corresponde `DECIMAL` + `BigDecimal`: mientras siga
  así, los totales acumulan error de redondeo.

## 0.1.0 (2026-07-06)

### Funcionalidades
- CRUD de productos con búsqueda por código de barras y soft delete
- Registro de ventas con soporte de productos del sistema y productos manuales
- Descuento automático de stock al realizar ventas
- Registro de compras/ingresos con aumento automático de stock
- Gestión de lotes con seguimiento de vencimientos (vigentes, próximos a vencer, vencidos)
- Ajuste de stock por conteo físico
- Trazabilidad completa via movimientos de stock (COMPRA, VENTA, AJUSTE, MERMA)
- Roles de usuario: ADMIN y EMPLEADO

### Técnico
- Spring Boot 3.5.13 + Java 21 + MySQL
- API REST documentada con SpringDoc OpenAPI 2.5.0
- Soft delete en Producto, Usuario y Lote
- Mapeo polimórfico de detalles de venta (producto sistema / manual)
- Arquitectura en capas: Controller -> Service -> Repository
