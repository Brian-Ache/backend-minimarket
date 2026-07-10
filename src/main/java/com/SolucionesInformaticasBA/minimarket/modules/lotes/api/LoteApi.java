package com.SolucionesInformaticasBA.minimarket.modules.lotes.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteResponse;

public interface LoteApi {
    LoteResponse crear(UUID idUsuario, LoteRequest request);

    List<LoteResponse> getAll();

    List<LoteResponse> getByEstado(String estado);
}
