## para la version 0.2.0

### Implementado

    - modules: extraidos usuarios y autenticacion, con sus capas y contratos facade de acceso al modulo
    - shhared: (comparitdo) con los manejadores de exepciones basicos
    - security: implementacion simple de cors y jwt con claimb de rol y id en el token

### justificaciones de cambios

    - cambie varios id de Long a UUID porque es mas seguro al no ser incrementales y se generan con una marca de tiempo
    - los joinColumn son innecesarios si no trabajamos con herencia, ademas es mas rapido y menos costodo buscarlo por el id de la entidad y para los indices en db
    - agrego el campo mail a usuarios para recuperar pass o futuras implementaciones de notificaciones
    - sagrego campo enabled a usuarios para bloqueo logico (sin que sea soft delete) o para activacion de la cuenta (manual, OTP o email token) por ahora enabled es true por default
    - refactorice partes del codigo para hacer compatible los modulos nuevos con lo legacy

### recomendaciones

    - implementar un estandard de timestamps (creacion, actualizacion y eliminado) preferentemente en ingles ya que es el estandar para auditorias y logs
    - revisar todas las entidades que quedaron con id Long y si no es necesario que tengan un id incremental cambiar a UUID
    - revisar la implementacion de tablas pivote para no meter listas de entidades dentro de otra entidad, manejar al reves, es menos costoso paraconsultas y mas rapido con los indices
    - seguir migrando y refactorizando modulos a medida que se necesiten
    - eliminar lo legacy de usuarios para evitar duplicidad
    - a medida que se migren o creen modulos mantener los principios de responsabilidad unica accesiendo al modulo mediante la interfaz api (contrato facade)
    - si no hay demasiados mapper y helpers mantener en el service para mantener el codigo, separarlo ya implica arquitectura hexagonal y casos de uso
    - usar builder en las entidades y helpers
