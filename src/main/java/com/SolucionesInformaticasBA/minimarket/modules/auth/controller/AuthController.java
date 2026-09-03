package com.SolucionesInformaticasBA.minimarket.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.InvitacionResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Autenticación",
     description = """
             Login, sesiones y los dos circuitos que se resuelven por mail: el cierre del alta \
             por invitación y el reseteo de contraseña.

             Todos estos endpoints son **públicos**: quien los llama todavía no tiene sesión, o \
             directamente no tiene contraseña y su única credencial es el token que le llegó al \
             mail. El resto de la API exige `Authorization: Bearer <accessToken>`.""")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Forma del cuerpo de error que arma {@code GlobalExceptionHandler} para toda la API. */
    private static final String SCHEMA_ERROR = """
            {"error": "string", "timestamp": "2026-09-03T14:22:51.336"}""";

    private final AuthApi authApi;

    // El alta de usuarios es exclusiva del ADMIN, y siempre por invitación:
    // POST /api/users/v1/invitaciones

    @Operation(summary = "Iniciar sesión",
               description = """
                       Devuelve el access token, el refresh token y los datos del usuario.

                       El campo `username` acepta **el email o el nombre de usuario**, \
                       indistinto; conserva ese nombre por compatibilidad con el front.

                       El error es siempre el mismo —credenciales inválidas— exista o no la \
                       cuenta y esté como esté: distinguirlos dejaría deducir qué cuentas hay \
                       y en qué estado están.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesión iniciada"),
        @ApiResponse(responseCode = "400", description = "Faltan campos obligatorios",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR))),
        @ApiResponse(responseCode = "401",
            description = "Credenciales inválidas, o la cuenta no está activa",
            content = @Content(examples = @ExampleObject(
                value = "{\"error\": \"Credenciales inválidas\", \"timestamp\": \"2026-09-03T14:22:51.336\"}")))
    })
    @PostMapping("/v1/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authApi.login(request));
    }

    @Operation(summary = "Renovar la sesión",
               description = """
                       Entrega un access token nuevo y **rota el refresh token**: el que se \
                       envía queda revocado en el mismo movimiento, así que hay que guardar el \
                       que vuelve.

                       Una cuenta dada de baja no puede renovar, aunque su refresh token siga \
                       vigente.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesión renovada"),
        @ApiResponse(responseCode = "400",
            description = "Refresh token inválido, revocado o expirado",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR))),
        @ApiResponse(responseCode = "404", description = "El usuario ya no existe",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR)))
    })
    @PostMapping("/v1/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authApi.refreshToken(request));
    }

    @Operation(summary = "Cerrar la sesión",
               description = """
                       Revoca el refresh token. El access token sigue siendo válido hasta que \
                       expire (`jwt.expiration-hours`), así que el front tiene que descartarlo.

                       Es idempotente: desloguear un token ya revocado, expirado o inexistente \
                       responde `204` igual.""")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    @PostMapping("/v1/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authApi.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consultar una invitación",
               description = """
                       Datos para que el formulario de aceptación salude a la persona y le \
                       precargue el nombre de usuario sugerido.

                       **No consume el token**, así que también sirve para detectar un enlace \
                       vencido antes de hacerle llenar el formulario.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "La invitación sigue en pie"),
        @ApiResponse(responseCode = "400",
            description = "Token inválido, vencido, ya usado o de otro tipo; o la cuenta se "
                        + "bloqueó o se dio de baja desde que se envió la invitación",
            content = @Content(examples = @ExampleObject(
                value = "{\"error\": \"La invitación ya no es válida\", \"timestamp\": \"2026-09-03T14:22:51.336\"}")))
    })
    @GetMapping("/v1/invitacion")
    public ResponseEntity<InvitacionResponse> consultarInvitacion(
            @Parameter(description = "El token del enlace del mail", required = true,
                       schema = @Schema(type = "string"))
            @RequestParam String token) {
        return ResponseEntity.ok(authApi.consultarInvitacion(token));
    }

    @Operation(summary = "Aceptar una invitación",
               description = """
                       Cierra el alta: define la contraseña, opcionalmente el nombre de \
                       usuario, y pasa la cuenta a `ACTIVO`. El token se quema, sirve una sola \
                       vez.

                       Sin `username` queda el que se derivó del email al invitar —el mismo que \
                       devuelve `GET /api/auth/v1/invitacion` como `usernameSugerido`—.

                       No devuelve sesión: después hay que loguearse normalmente.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta activada"),
        @ApiResponse(responseCode = "400",
            description = "Token inválido, vencido, ya usado o de otro tipo; la cuenta se "
                        + "bloqueó o se dio de baja entre la invitación y la aceptación; o el "
                        + "nombre de usuario elegido ya está tomado",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR))),
        @ApiResponse(responseCode = "404", description = "El usuario ya no existe",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR)))
    })
    @PostMapping("/v1/invitacion/aceptar")
    public ResponseEntity<Void> aceptarInvitacion(@Valid @RequestBody AceptarInvitacionRequest request) {
        authApi.aceptarInvitacion(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Pedir el reseteo de contraseña",
               description = """
                       Manda al email de la cuenta un enlace para elegir una contraseña nueva. \
                       El campo `username` acepta email o nombre de usuario, igual que el login.

                       **Responde `200` siempre**, exista o no la cuenta, y también si el envío \
                       falla: un error solo cuando la cuenta existe revelaría cuáles existen. \
                       Que devuelva `200` no significa entonces que haya salido un mail.""")
    @ApiResponse(responseCode = "200", description = "Pedido recibido")
    @PostMapping("/v1/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authApi.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Confirmar el reseteo de contraseña",
               description = """
                       Define la contraseña nueva con el token del mail, que dura una hora y \
                       sirve una sola vez.

                       **Cierra todas las sesiones abiertas** del usuario: si alguien tenía la \
                       contraseña vieja, sus tokens dejan de valer.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña actualizada"),
        @ApiResponse(responseCode = "400",
            description = "Token inválido, vencido, ya usado o de otro tipo",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR))),
        @ApiResponse(responseCode = "404", description = "El usuario ya no existe",
            content = @Content(examples = @ExampleObject(value = SCHEMA_ERROR)))
    })
    @PostMapping("/v1/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authApi.confirmPasswordReset(request);
        return ResponseEntity.ok().build();
    }
}
