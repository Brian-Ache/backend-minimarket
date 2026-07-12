package com.SolucionesInformaticasBA.minimarket.modules.categorias.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;

public interface CategoriasApi {
    CategoriaResponse crear(CategoriaRequest request);
    CategoriaResponse getById(UUID id);
    List<CategoriaResponse> getAll();
    CategoriaResponse update(UUID id, CategoriaRequest request);
    void delete(UUID id);
    boolean existsById(UUID id);
}
