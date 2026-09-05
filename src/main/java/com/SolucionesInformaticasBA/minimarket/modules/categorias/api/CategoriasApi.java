package com.SolucionesInformaticasBA.minimarket.modules.categorias.api;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;

public interface CategoriasApi {
    CategoriaResponse crear(CategoriaRequest request);
    CategoriaResponse getById(UUID id);
    /** Paginado, como el resto de los listados de la API. */
    Page<CategoriaResponse> getAll(Pageable pageable);
    CategoriaResponse update(UUID id, CategoriaRequest request);
    void delete(UUID id);
    boolean existsById(UUID id);
}
