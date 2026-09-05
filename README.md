# backend-minimarket

Backend de un punto de venta para minimarket: catálogo, ventas con cobro, compras a proveedores,
inventario con lotes y vencimientos, caja con arqueo y reportes.

API REST con Spring Boot 3.5 sobre Java 21 y MySQL 8, autenticación por JWT y autorización por
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

## Tests

```bash
./mvnw test
```

258 tests unitarios con JUnit 5 y Mockito. `MinimarketApplicationTests.contextLoads` es el único
que necesita la base levantada; si no está, falla con *Communications link failure* y el resto
pasa igual.

## Estructura

```
src/main/java/…/minimarket/
├── config/        CORS y PasswordEncoder
├── security/      SecurityConfig, JwtProvider, filtro de autenticación
├── shared/        utilidades transversales, mails, excepciones y su handler
└── modules/       auth, usuarios, categorias, proveedores, productos,
                   inventario, ventas, compras, caja, reportes
script/database/   esquema, seeds y migraciones numeradas
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
