package com.SolucionesInformaticasBA.minimarket.modules.usuarios.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.InvitarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioApi usuarioApi;

    // hasRole('ADMIN') alcanza también al SUPERADMIN: el filtro JWT le da las authorities de
    // todos los roles por debajo del suyo. Qué rol puede repartir cada uno, y sobre quién puede
    // operar, lo decide UsuarioService, que es donde se conoce el rol del objetivo.

    /**
     * Alta por invitación: la persona recibe un mail y define ahí su contraseña. Es el único
     * camino de alta —no hay endpoint que cree una cuenta con la contraseña ya puesta—, así que
     * quien invita nunca conoce la credencial de quien invitó.
     */
    @PostMapping("/v1/invitaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> invitar(@Valid @RequestBody InvitarUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioApi.invitar(request));
    }

    /** Manda de nuevo la invitación, con token nuevo. Solo si la cuenta sigue PENDIENTE. */
    @PostMapping("/v1/{id}/invitaciones/reenviar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reenviarInvitacion(@PathVariable UUID id) {
        usuarioApi.reenviarInvitacion(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/me")
    public ResponseEntity<UsuarioResponse> getMe() {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(usuarioApi.getById(userId));
    }

    @GetMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.principal")
    public ResponseEntity<UsuarioResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioApi.getById(id));
    }

    /**
     * @param incluirBajas suma las cuentas dadas de baja, que vienen con {@code deletedAt}
     *        cargado. Es cómo el front encuentra la que hay que restaurar: en el listado
     *        normal no aparecen.
     */
    @GetMapping("/v1")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UsuarioResponse>> getAll(
            @RequestParam(defaultValue = "false") boolean incluirBajas,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        // Por username, que es único entre las cuentas activas, con el id como desempate: el
        // unique no mira deleted_at, pero una baja y su restauración comparten fila, así que
        // el empate real solo puede darse entre una cuenta viva y otra dada de baja.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "username").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(usuarioApi.getAll(incluirBajas, pageable));
    }

    @PatchMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.principal")
    public ResponseEntity<UsuarioResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioApi.update(id, request));
    }

    /**
     * Promueve o degrada a un usuario. Va aparte del PATCH general porque ese lo puede llamar
     * el dueño del recurso sobre sí mismo, y nadie se cambia el rol solo.
     */
    @PatchMapping("/v1/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> cambiarRol(@PathVariable UUID id,
            @Valid @RequestBody CambiarRolRequest request) {
        return ResponseEntity.ok(usuarioApi.cambiarRol(id, request));
    }

    /** Suspende el acceso sin borrar la cuenta. Corta las sesiones abiertas del usuario. */
    @PostMapping("/v1/{id}/bloquear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> bloquear(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioApi.bloquear(id));
    }

    @PostMapping("/v1/{id}/desbloquear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> desbloquear(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioApi.desbloquear(id));
    }

    @DeleteMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        usuarioApi.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revive una cuenta dada de baja: vuelve como PENDIENTE y le llega una invitación nueva
     * para que defina otra contraseña. Es la contracara del DELETE, con su misma jerarquía.
     *
     * <p>Va sobre la fila original y no sobre una cuenta nueva, para que la persona no pierda
     * su historial. Las cuentas dadas de baja se listan con
     * {@code GET /api/users/v1?incluirBajas=true}.
     */
    @PostMapping("/v1/{id}/restaurar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> restaurar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioApi.restaurar(id));
    }

    // Solo el dueño: cambiar la contraseña exige conocer la actual, así que ni el ADMIN
    // puede hacerlo por otro (para eso está el flujo de reseteo).
    @PostMapping("/v1/{id}/change-password")
    @PreAuthorize("#id.toString() == authentication.principal")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id,
            @Valid @RequestBody CambiarPasswordRequest request) {
        usuarioApi.changePassword(id, request);
        return ResponseEntity.ok().build();
    }
}
