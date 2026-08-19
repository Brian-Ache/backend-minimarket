package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Data
@Builder
public class DetalleCompraRequest {
    @NotNull
    private UUID idProducto;

    @PositiveOrZero
    private float precioUnitario;

    @Positive
    private int cantidad;

    // opcional para lotes, pueden ser null
    private LocalDate fechaVencimiento;
    private String numeroLote;
}
