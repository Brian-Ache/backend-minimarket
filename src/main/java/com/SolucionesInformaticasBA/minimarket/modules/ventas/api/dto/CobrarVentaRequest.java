package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class CobrarVentaRequest {
    @NotNull
    @PositiveOrZero
    private Float montoRecibido;

    @NotBlank
    private String metodoPago;
}
