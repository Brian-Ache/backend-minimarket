package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Data
@Builder
public class StockRequest {
    @NotNull
    private UUID idProducto;

    @PositiveOrZero
    private int cantidad;
}
