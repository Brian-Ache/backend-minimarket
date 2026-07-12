package com.SolucionesInformaticasBA.minimarket.modules.ventas.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;

public interface VentasApi {
    VentaResponse realizarVenta(UUID idUsuario, VentaRequest request);
    VentaResponse getById(UUID id);
    List<VentaResponse> getAll();
    List<VentaResponse> getByUsuario(UUID idUsuario);
    List<VentaResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta);
    void delete(UUID id);
}
