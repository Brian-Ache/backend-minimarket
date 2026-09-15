package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteRequest {
    @NotNull
    private UUID idProducto;

    private String numeroLote;

    @NotNull
    private LocalDate fechaVencimiento;

    @Positive
    private int cantidad;
}
