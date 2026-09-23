# Ideas y correcciones

> **Bandeja de entrada.** Anotá acá lo que se te ocurra, como te salga: una línea suelta alcanza.
> No hace falta que esté completo, ordenado ni bien explicado —para eso está la conversación
> después—.
>
> **Cómo sigue:** cuando quieras, lo revisamos juntos. Yo lo contrasto contra el código, te
> pregunto lo que haga falta para decidir bien, y lo que quede definido pasa a un plan de
> implementación con fases. Lo que se implementa sale de acá y queda documentado en el
> [`CHANGELOG`](../CHANGELOG.md); lo que se decide postergar, en [`roadmap.md`](roadmap.md).

---

## Pendientes de conversar

<!--
Escribí libremente debajo. Si te sirve una guía, con esto alcanza:

  - qué pasa hoy que no te gusta, o qué falta
  - si sabés dónde (pantalla, endpoint, módulo), mejor; si no, no importa

No hace falta que propongas la solución: muchas veces el mejor camino aparece
recién cuando miramos el código.
-->

_(vacío)_

---

## En análisis

<!-- Lo que ya estamos discutiendo pero todavía no tiene plan cerrado. -->

- **Despliegue automático al VPS con nginx.** Las cuatro fases del Track B **ya están escritas**:
  imagen y CI (B1, B2) publicadas en la 0.6.0, y el workflow de publicación y despliegue
  (B3, B4) en `.github/workflows/deploy.yml`, con las plantillas del servidor en
  `docker/produccion/`. La decisión del healthcheck quedó cerrada: entró
  `spring-boot-starter-actuator` con solo `/actuator/health` expuesto.

  **Lo que falta no es código:** la cuenta de Docker Hub, el VPS y el dominio. Concretamente, los
  siete secrets del repo y la puesta a punto del servidor que documenta el README. Hasta que eso
  exista, el workflow no se puede probar de punta a punta.
- **Sincronización offline-first de tickets.** **Hecha y publicada en la 0.6.0**: el Track A
  entero, de A1 a A7. El contrato para el front está en
  [`api-endpoints.md`](api-endpoints.md) y el porqué de cada regla en
  [`ARCHITECTURE.md`](../ARCHITECTURE.md). Lo que queda abierto es del lado del front, listado en
  la sección 6 del [documento de requisitos](../requisitos-sync-offline-tickets%20(1).md).

---

## Descartado

<!--
Lo que se miró y se decidió no hacer, con el motivo en una línea. Sirve para no
volver a proponerlo dentro de seis meses sin acordarse de por qué se bajó.
-->

_(vacío)_
