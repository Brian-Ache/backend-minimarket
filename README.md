# backend-minimarket

Backend de un punto de venta para minimarket: catálogo, ventas con cobro, compras a proveedores,
inventario con lotes y vencimientos, caja con arqueo y reportes.

API REST con Spring Boot 4.1 sobre Java 21 y MySQL 8, autenticación por JWT y autorización por
rol. Se despliega **una instancia por comercio**: el aislamiento entre comercios lo da el
despliegue, no el modelo de datos.

## Levantarlo

Hace falta Java 21 y un MySQL 8. El wrapper de Maven viene incluido, no hay que instalarlo.

**1. La base.** Con Docker, para no ensuciar la máquina:

```bash
docker run -d --name minimarket-db -p 3306:3306 -e MYSQL_ROOT_PASSWORD=test123 mysql:8.0
docker exec -i minimarket-db mysql -uroot -ptest123 --default-character-set=utf8mb4 < script/database/00_init_limpio.sql
docker exec -i minimarket-db mysql -uroot -ptest123 --default-character-set=utf8mb4 < script/database/01_seed.sql
```

> **`--default-character-set=utf8mb4` no es opcional.** El cliente `mysql` del contenedor usa
> latin1 por defecto, así que sin el flag los acentos entran doble-codificados: la categoría
> "Almacén" queda guardada como `Almacén` y la API la devuelve así. Las columnas ya son
> `utf8mb4`; lo que rompe es la conexión del cliente.

`00_init_limpio.sql` es el esquema completo y autocontenido: una instalación nueva **no** necesita
las migraciones numeradas. `01_seed.sql` carga un superadmin y un admin de desarrollo.

**2. La configuración.** Copiá `.env.example` y completá `JWT_SECRET`, que es la única variable
obligatoria — sin ella la app no arranca, a propósito:

```bash
cp .env.example .env
openssl rand -base64 48   # pegar el resultado en JWT_SECRET
```

**3. La aplicación.** La configuración se lee de **variables de entorno**, no del archivo: el
proyecto no incluye ninguna librería que cargue `.env` solo, así que hay que exportarlo antes.

```bash
set -a; source .env; set +a
./mvnw spring-boot:run
```

Queda en `http://localhost:8080`, con Swagger UI en `/swagger-ui/index.html`.

**Usuarios del seed:** `admin@minimarket.local` / `Admin123!` y `superadmin@minimarket.local` /
`Super123!`. Son de desarrollo: cambiá las contraseñas antes de exponer el sistema.

> **No hace falta un SMTP para trabajar.** Sin `MAIL_HOST` la app no manda mails: los deja en el
> log con el enlace de invitación o de reseteo incluido, que es lo único que se necesita para
> completar esos flujos en desarrollo.

## Levantarlo con Docker

La alternativa a los tres pasos de arriba: `compose.yaml` levanta la API y su MySQL juntos, sin
instalar nada más que Docker.

```bash
cp .env.example .env    # completar JWT_SECRET y DB_PASSWORD
docker compose up --build
```

Compose lee el `.env` del directorio **solo**, así que acá no hace falta el `set -a; source .env`.
Si falta alguna de las dos variables obligatorias, falla antes de construir nada y dice cuál.

La primera vez, MySQL corre `00_init_limpio.sql` y `01_seed.sql` desde el volumen y queda con el
esquema y los usuarios de desarrollo. **Esos scripts no vuelven a correr**: el `docker-entrypoint`
solo los aplica con el volumen vacío. Para rehacer la base desde cero:

```bash
docker compose down -v && docker compose up --build
```

Tres cosas que el compose resuelve y conviene saber que están ahí:

- **La imagen no migra la base.** Arranca con `JPA_DDL_AUTO=none` y espera el esquema ya aplicado;
  mientras no exista Flyway, las migraciones de `script/database/` se corren a mano y con la
  aplicación detenida. El contenedor no lo hace solo y no va a avisar: una columna que falta
  aparece como un error de Hibernate en el primer request que la toca.
- **El charset.** `docker/mysql/utf8mb4.cnf` fuerza `utf8mb4` en el cliente del contenedor. Sin
  eso los scripts de inicialización entran doble-codificados y la categoría "Almacén" queda
  guardada como `Almacén`, con las columnas ya en `utf8mb4` y sin ningún error a la vista.
- **La zona horaria.** El contenedor corre en `America/Argentina/Buenos_Aires`, no en UTC. Un
  punto de venta que fecha sus ventas, sus movimientos de caja y sus cortes tres horas adelantados
  no sirve. Se cambia con `TZ` en el `.env`.

> **`compose.yaml` no es el archivo de producción.** Siembra usuarios con contraseñas conocidas y
> publica el puerto de MySQL. El despliegue al VPS usa su propio compose, que baja la imagen
> publicada en vez de construirla.

## Tests

```bash
./mvnw test
```

270 tests unitarios con JUnit 6 y Mockito. `MinimarketApplicationTests.contextLoads` es el único
que necesita la base levantada; si no está, falla con *Communications link failure* y el resto
pasa igual.

Para correr solo lo que no necesita base —que es lo que hace el CI—:

```bash
./mvnw -B verify -DexcludedGroups=integracion
```

Todo test que necesite MySQL va marcado con `@Tag("integracion")`.

## CI

`.github/workflows/ci.yml` corre `./mvnw -B verify` en cada push a `main` o `developer` y en
cada PR contra ellas, con JDK 21 y cache de Maven. Para que un PR con un test roto no se pueda
mergear hay que marcar el check **Build y tests** como obligatorio en la protección de rama de
GitHub (*Settings → Branches*); el workflow por sí solo reporta, no bloquea.

## Estructura

```
src/main/java/…/minimarket/
├── config/        CORS y PasswordEncoder
├── security/      SecurityConfig, JwtProvider, filtro de autenticación
├── shared/        utilidades transversales, mails, excepciones y su handler
└── modules/       auth, usuarios, categorias, proveedores, productos,
                   inventario, ventas, compras, caja, reportes
script/database/   esquema, seeds y migraciones numeradas
docker/mysql/      configuración del MySQL del compose
docs/              documentación (ver docs/README.md)
```

Cada módulo agrupa su entidad, repositorio, servicio, controlador y contrato público, y se
comunica con los demás **solo** a través de su interfaz `XxxApi`.

## Documentación

| Dónde | Qué |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Cómo está construido y **por qué** quedó así: módulos, seguridad, modelo de datos, concurrencia |
| [`CHANGELOG.md`](CHANGELOG.md) | Qué cambió en cada versión, con los cambios incompatibles y las migraciones |
| [`docs/api-endpoints.md`](docs/api-endpoints.md) | Referencia de la API: request, response y errores de cada endpoint |
| [`docs/ideas.md`](docs/ideas.md) | Ideas y correcciones sin procesar, para conversar |
| [`docs/roadmap.md`](docs/roadmap.md) | Deuda técnica y funcionalidades propuestas |

Índice completo en [`docs/README.md`](docs/README.md).

## Actualizar una instalación existente

Las migraciones de `script/database/` se aplican **a mano, en orden y con la aplicación
detenida**. Cada versión del `CHANGELOG` dice cuáles hacen falta; cada script abre con una
consulta de diagnóstico y cierra con una de verificación.

```bash
mysql -u root -p < script/database/09_stock_de_productos_con_lotes.sql
```

Incorporar Flyway es trabajo pendiente, anotado en el roadmap.
