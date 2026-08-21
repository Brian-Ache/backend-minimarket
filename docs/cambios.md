## Roles

    -implementar superadmin como llave maestra y dueno del sistema
    -superadmin: administra el sistema, gestion de usuarios y bloqueo de los mismos
    -admin: es un usuario del sistema como tal, es quien paga por el mismo y da de alta a los empleados
    -empleado: operario del sistema, realiza las acciones que el admin habilite

## Login

    - implementar que se pueda iniciar sesion con email y con username

## Registro

    - admin da de alta a los empleados
    - por defecto los usuarios estan enabled = true
    - superadmin da de alta a un admin y admin a los empleados (superadmin tambien podria en algun caso dar de alta un empleado)
    - el flujo de registro es por "invitacion" el admin llena el formulario con email, nombre y rol. Le llega la invitacion por mail a quien se quiere dar de alta (introducir SMTP y algun correo no-reply o propio de quien contrata)

## Auth

    - la recuperacion de contrasena se hara mediante email
