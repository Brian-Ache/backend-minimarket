package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AjusteStockRequest {
    @NotNull
    private UUID idProducto;

    /** Conteo físico. Puede ser 0, nunca negativo. */
    @PositiveOrZero
    private int stockReal;

    private String tipo;
    private String motivo;
}
