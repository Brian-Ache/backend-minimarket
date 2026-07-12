package com.SolucionesInformaticasBA.minimarket.modules.compras.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;

public interface CompraApi {
    CompraResponse crear(UUID idUsuario, CompraRequest request);
    CompraResponse getById(UUID id);
    List<CompraResponse> getAll();
    List<CompraResponse> getByUsuario(UUID idUsuario);
    List<CompraResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta);
    void delete(UUID id);
}
