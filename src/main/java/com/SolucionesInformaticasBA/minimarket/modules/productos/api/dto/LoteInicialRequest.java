package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/**
 * Datos del primer lote de un producto que maneja lotes, dentro del alta.
 *
 * <p>La fecha de vencimiento es obligatoria por el mismo motivo por el que la exige el alta de
 * lotes del módulo de inventario: un lote sin fecha queda en {@code SIN_FECHA}, invisible para
 * el control de vencimientos, y la mercadería vencida no aparece en ningún lado.
 */
@Data
@Builder
public class LoteInicialRequest {
    private String numeroLote;

    @NotNull
    private LocalDate fechaVencimiento;
}
