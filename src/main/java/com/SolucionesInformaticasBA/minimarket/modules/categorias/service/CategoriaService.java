package com.SolucionesInformaticasBA.minimarket.modules.categorias.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.entity.Categoria;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.repository.CategoriaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CategoriaService implements CategoriasApi {
    private final CategoriaRepository categoriaRepository;

    @Override
    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        if (categoriaRepository.findByNombreAndDeletedAtIsNull(request.getNombre()).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre");
        }

        Categoria categoria = Categoria.builder()
            .nombre(request.getNombre())
            .descripcion(request.getDescripcion())
            .build();

        return toResponse(categoriaRepository.save(categoria));
    }

    @Override
    public CategoriaResponse getById(UUID id) {
        Categoria categoria = categoriaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        return toResponse(categoria);
    }

    @Override
    public List<CategoriaResponse> getAll() {
        return categoriaRepository.findAll().stream()
            .filter(c -> c.getDeletedAt() == null)
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public CategoriaResponse update(UUID id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));

        if (!categoria.getNombre().equals(request.getNombre())
                && categoriaRepository.existsByNombreAndDeletedAtIsNull(request.getNombre())) {
            throw new BadRequestException("Ya existe una categoría con ese nombre");
        }

        categoria.setNombre(request.getNombre());
        categoria.setDescripcion(request.getDescripcion());
        return toResponse(categoriaRepository.save(categoria));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Categoria categoria = categoriaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        categoria.setDeletedAt(LocalDateTime.now());
        categoriaRepository.save(categoria);
    }

    @Override
    public boolean existsById(UUID id) {
        return categoriaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    private CategoriaResponse toResponse(Categoria c) {
        return CategoriaResponse.builder()
            .id(c.getId())
            .nombre(c.getNombre())
            .descripcion(c.getDescripcion())
            .build();
    }
}
