# Changelog

## Sin publicar

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

- `05_migracion_estado_usuario.sql` — migra `enabled` a `estado` (1 ⇒ `ACTIVO`, 0 ⇒
  `PENDIENTE`) y agrega el índice único de `username`. **Verificar antes que no haya usernames
  repetidos**: el script trae la consulta y explica cómo seguir si el índice falla.
- `06_migracion_superadmin.sql` — agrega `SUPERADMIN` al ENUM de `rol` y da de alta el
  superadmin (`superadmin@minimarket.local` / `Super123!`). **Cambiar esa contraseña apenas se
  ingresa.** Trae también la variante para promover una cuenta existente.
- `07_migracion_invitaciones.sql` — agrega `INVITATION` al ENUM de `auth_tokens.token_type`.
  Tipo propio y no reusar `VERIFICATION`: con el de verificación el usuario solo confirma su
  email, con el de invitación define su contraseña por primera vez. Si compartieran tipo, un
  token de verificación serviría para setear la contraseña de esa cuenta.

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

Tres migraciones acumulativas en `script/database/`, con verificación incluida:

- `02_migracion_fase3.sql` — `movimientos_stock.id_referencia` (base de las reversas), unicidad
  de stock por producto y de sesión de caja abierta, origen `REVERSA`.
- `03_migracion_fase4.sql` — desglose persistido del corte, `detalles_ventas.costo_unitario`,
  índice por fecha de cobro.
- `04_migracion_fase5.sql` — `refresh_token` pasa a `refresh_tokens`. **Aplicar con la app
  detenida:** invalida las sesiones abiertas.

Para una base nueva alcanza con `00_init.sql` + `01_seed.sql`, que ya incluyen todo. El esquema
valida contra las entidades con `spring.jpa.hibernate.ddl-auto=validate`.

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
