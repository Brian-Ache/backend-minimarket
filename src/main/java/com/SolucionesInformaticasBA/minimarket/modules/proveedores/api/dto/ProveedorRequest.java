package com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProveedorRequest {
    @NotBlank
    @Size(max = 150)
    private String nombre;

    @Size(max = 50)
    private String telefono;

    @Email(message = "El email del proveedor no tiene un formato válido")
    @Size(max = 100)
    private String email;

    @Size(max = 255)
    private String direccion;
}
