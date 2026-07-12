package com.SolucionesInformaticasBA.minimarket.dto.request;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

// DTO base para los detalles de venta, con anotaciones para deserialización polimórfica
//jackson se encargará de instanciar la clase correcta según el campo "tipo" en el JSON
//el campo "tipo" debe ser "PRODUCTO" para DetalleVentaProductoSistemaRequestDTO y "MANUAL" para DetalleVentaProductoManualRequestDTO
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "tipo"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = DetalleVentaProductoSistemaRequestDTO.class, name = "PRODUCTO"),
    @JsonSubTypes.Type(value = DetalleVentaProductoManualRequestDTO.class, name = "MANUAL")
})


@Data
public abstract class DetalleVentaRequestDTO {

    private int cantidad;
}