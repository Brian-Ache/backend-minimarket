package com.SolucionesInformaticasBA.minimarket.modules.proveedores.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

public interface ProveedoresApi {
    ProveedorResponse crear(ProveedorRequest request);
    ProveedorResponse getById(UUID id);
    List<ProveedorResponse> getAll();
    ProveedorResponse update(UUID id, ProveedorRequest request);
    void delete(UUID id);
    boolean existsById(UUID id);
}
