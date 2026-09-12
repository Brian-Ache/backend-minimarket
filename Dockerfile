# =====================================================================
# backend-minimarket — imagen de la API
# =====================================================================
# Dos etapas: una construye el jar con el wrapper del repo —la misma versión de Maven que
# usan el CI y la máquina de desarrollo—, la otra corre sobre un JRE pelado y sin Maven.
#
# La imagen NO migra la base. Arranca con JPA_DDL_AUTO=none y espera el esquema ya aplicado:
# mientras no exista Flyway, las migraciones de script/database/ se corren a mano y con la
# aplicación detenida. Ver la sección "Despliegue con Docker" del README.
# =====================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# El pom y el wrapper primero, solos: mientras no cambien, la capa de dependencias queda
# cacheada y un cambio en src/ no vuelve a bajar medio Maven Central.
#
# `lombok.config` no es opcional: es el que hace que Lombok copie el @Lazy al constructor que
# genera. Sin ese archivo el jar compila igual y la aplicación muere al arrancar con "the
# dependencies of some of the beans form a cycle" entre usuarioService y authService.
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Sin tests: los corre el CI antes de que esto se construya, y repetirlos acá solo agrega
# minutos a cada build de imagen.
RUN ./mvnw -B -ntp package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario propio, sin privilegios: si alguien sale del proceso Java, no sale como root.
RUN groupadd --gid 1001 minimarket \
    && useradd --uid 1001 --gid minimarket --no-create-home --shell /usr/sbin/nologin minimarket

COPY --from=build --chown=minimarket:minimarket /build/target/*.jar app.jar

USER minimarket
EXPOSE 8080

# JAVA_OPTS queda a mano para ajustar memoria en el VPS sin rehacer la imagen.
# El exec es lo que hace que el java quede como PID 1 y reciba el SIGTERM del docker stop.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
