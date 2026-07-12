package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class AbrirSesionRequest {
    @PositiveOrZero
    private float saldoInicial;
}
