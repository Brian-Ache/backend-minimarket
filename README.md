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
docker exec -i minimarket-db mysql -uroot -ptest123 --default-character-set=utf8mb4 < script/database/init.sql
```

> **`--default-character-set=utf8mb4` no es opcional.** El cliente `mysql` del contenedor usa
> latin1 por defecto, así que sin el flag los acentos entran doble-codificados: la categoría
> "Almacén" queda guardada como `Almacén` y la API la devuelve así. Las columnas ya son
> `utf8mb4`; lo que rompe es la conexión del cliente.

`init.sql` es el único script de la base: crea el esquema completo y carga el seed mínimo —un
superadmin, un admin de desarrollo y un catálogo chico—. Es idempotente, así que volver a correrlo
no rompe nada. Para un catálogo grande con el que probar listados y paginado, `seed_demo.sql`
agrega 81 productos con su stock; es opcional.

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

La primera vez, MySQL corre `init.sql` desde el volumen y queda con el esquema y los usuarios de
desarrollo. **Ese script no vuelve a correr**: el `docker-entrypoint` solo lo aplica con el
volumen vacío. Para rehacer la base desde cero:

```bash
docker compose down -v && docker compose up --build
```

Tres cosas que el compose resuelve y conviene saber que están ahí:

- **La imagen no migra la base.** Arranca con `JPA_DDL_AUTO=none` y espera el esquema ya aplicado;
  mientras no exista Flyway, todo cambio de esquema sobre una base ya cargada se aplica a mano y
  con la aplicación detenida. El contenedor no lo hace solo y no va a avisar: una columna que
  falta aparece como un error de Hibernate en el primer request que la toca.
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

## Despliegue

**Producción es `main`, y nada más que `main`.** Un push a `developer` compila y testea; un push
a `main` construye la imagen, la publica en Docker Hub y actualiza el VPS
(`.github/workflows/deploy.yml`). La única forma de llegar a producción es un merge a `main`.

### Secrets que hay que cargar en GitHub

*Settings → Secrets and variables → Actions.* Sin ellos el workflow falla en el primer paso.

| Secret | Qué es |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub; también es el prefijo del repositorio de la imagen |
| `DOCKERHUB_TOKEN` | Access token de Docker Hub, no la contraseña |
| `VPS_HOST` | IP o dominio del servidor |
| `VPS_USER` | Usuario SSH con permiso sobre Docker |
| `VPS_SSH_KEY` | Clave privada SSH completa, con sus líneas `BEGIN`/`END` |
| `VPS_RUTA_PROYECTO` | Directorio del VPS donde viven el compose y el `.env` |
| `URL_PUBLICA` | `https://api.tudominio.com`, para verificar el despliegue desde afuera |

### Puesta a punto del VPS, una sola vez

`docker/produccion/` tiene las plantillas. El `.env` de producción **no se versiona**: vive en el
servidor y nada más.

1. Copiar al directorio del proyecto en el VPS: `compose.yaml`, `utf8mb4.cnf` y `.env.example`
   como `.env`, completado y con `chmod 600`.
2. Aplicar el esquema, que la imagen **no** migra sola — y sin el seed de desarrollo, que trae
   contraseñas conocidas:
   ```bash
   docker compose up -d db
   docker compose exec -T db mysql -uroot -p"$DB_ROOT_PASSWORD" \
       --default-character-set=utf8mb4 < init.sql
   ```
   Después cambiar la contraseña del superadmin y del admin que el seed deja puestas.
3. nginx: `docker/produccion/nginx.conf` en `/etc/nginx/sites-available/`, enlazado en
   `sites-enabled/`, con el dominio real reemplazado. El TLS lo pone certbot:
   `sudo certbot --nginx -d api.tudominio.com`.
4. `docker compose up -d` y listo: a partir de ahí despliega el workflow.

### Por qué un despliegue falla en vez de quedar verde y roto

En producción la app corre con `JPA_DDL_AUTO=validate`, no con `none`. Hibernate no modifica
nada: compara el esquema con las entidades al arrancar y, si falta una tabla o una columna, no
levanta. El contenedor nunca llega a `healthy`, el `docker compose up -d --wait` del workflow
falla y el despliegue se corta ahí.

Sin eso, una base sin migrar pasa desapercibida: el healthcheck solo hace ping a MySQL, así que
el contenedor se reporta sano **incluso contra una base vacía** y el problema aparece media hora
después como un 500 en la caja. El precio es que un cambio de esquema hay que aplicarlo **antes**
de mergear a `main`.

El rollback es fijar el tag anterior en `IMAGEN_TAG` del `.env` del VPS y correr
`docker compose up -d`.

## Estructura

```
src/main/java/…/minimarket/
├── config/        CORS y PasswordEncoder
├── security/      SecurityConfig, JwtProvider, filtro de autenticación
├── shared/        utilidades transversales, mails, excepciones y su handler
└── modules/       auth, usuarios, categorias, proveedores, productos,
                   inventario, ventas, compras, caja, reportes
script/database/   init.sql (esquema + seed) y seed_demo.sql (datos de demo)
docker/mysql/      configuración del MySQL del compose de desarrollo
docker/produccion/ plantillas del VPS: compose, nginx y .env de producción
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

`init.sql` es para bases nuevas: crea las tablas con `IF NOT EXISTS`, así que **no** modifica una
base ya cargada. Mientras no exista Flyway, los cambios de esquema sobre una instalación en
producción se aplican a mano y con la aplicación detenida.

Las migraciones numeradas que llevaron el esquema hasta la 0.6.0 (`04_…` a `13_…`) quedaron en el
historial de git, en el commit `5ad8e94`, y se recuperan con:

```bash
git show 5ad8e94:script/database/13_importes_decimal_ventas.sql
```

Cada versión del `CHANGELOG` dice cuáles hacen falta. Incorporar Flyway es trabajo pendiente,
anotado en el roadmap.
