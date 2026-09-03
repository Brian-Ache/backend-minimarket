package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MovimientoCajaRequest {
    @Positive(message = "El monto debe ser mayor a 0")
    private float monto;

    @Size(max = 255)
    private String motivo;
}
