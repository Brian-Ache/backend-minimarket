package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class CobrarVentaRequest {
    /**
     * Lo que entrega el cliente, para calcular el vuelto. Solo tiene sentido en efectivo:
     * obligarlo en tarjeta y transferencia hacía que el cajero tuviera que inventar un número
     * —normalmente el total exacto— para que el cobro pasara, y ese número quedaba guardado
     * como si fuera un dato real. Con esos medios se ignora.
     */
    @PositiveOrZero
    private Float montoRecibido;

    @NotBlank
    private String metodoPago;
}
