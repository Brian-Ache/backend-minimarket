package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import com.SolucionesInformaticasBA.minimarket.shared.validation.Password;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CambiarPasswordRequest {

    /**
     * Sin {@code @Password} a propósito: acá no se hashea nada, se compara contra el hash
     * guardado, y {@code matches()} devuelve false sin romperse por largo que venga. Exigirle el
     * formato nuevo dejaría encerrado a quien haya quedado con una contraseña que Spring
     * Security 6 truncaba en silencio: no podría ni cambiarla.
     */
    @NotBlank
    private String passActual;

    @NotBlank
    @Password
    private String nuevoPass;
}
