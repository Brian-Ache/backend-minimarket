package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CambiarPasswordRequest {

    @NotBlank
    private String passActual;

    @NotBlank
    @Size(min = 8, max = 72)
    private String nuevoPass;
}