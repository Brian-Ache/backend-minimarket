package com.SolucionesInformaticasBA.minimarket.modules.inventario.enums;

import java.time.LocalDate;

public enum EstadoLote {
    SIN_FECHA,
    VENCIDO,
    PROXIMO,
    VIGENTE;

    /** Días antes del vencimiento a partir de los cuales un lote se considera PROXIMO. */
    public static final int DIAS_PROXIMO_A_VENCER = 7;

    /**
     * El estado depende del día en que se consulta, así que se deriva y no se almacena como
     * verdad: un lote VIGENTE pasa a VENCIDO sin que nadie lo toque.
     */
    public static EstadoLote calcularPara(LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) return SIN_FECHA;

        LocalDate hoy = LocalDate.now();
        if (fechaVencimiento.isBefore(hoy)) return VENCIDO;
        if (fechaVencimiento.isBefore(hoy.plusDays(DIAS_PROXIMO_A_VENCER))) return PROXIMO;
        return VIGENTE;
    }
}
