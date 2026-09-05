package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Data;

/** Precio que un proveedor lista por un producto. Se carga y se corrige siempre a mano. */
@Data
@Builder
public class PrecioReferenciaRequest {
    @NotNull
    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    private BigDecimal precioReferencia;
}
