package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

import lombok.Builder;
import lombok.Data;

/**
 * Un proveedor del catálogo de referencia del producto, con el precio que lista.
 *
 * <p>El proveedor viene resuelto incluyendo las bajas, con {@code deletedAt} cargado: dar de
 * baja a un proveedor no borra los precios que se le conocían, porque la baja es reversible y
 * perder el catálogo en cada una sería destructivo.
 */
@Data
@Builder
public class PrecioReferenciaResponse {
    private ProveedorResponse proveedor;
    private BigDecimal precioReferencia;

    /** Cuándo se cargó o se corrigió este precio, que es lo que dice si sigue sirviendo. */
    private LocalDateTime actualizado;
}
