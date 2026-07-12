package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MovimientoCajaRequest {
    @PositiveOrZero
    private float monto;

    @Size(max = 255)
    private String motivo;
}
