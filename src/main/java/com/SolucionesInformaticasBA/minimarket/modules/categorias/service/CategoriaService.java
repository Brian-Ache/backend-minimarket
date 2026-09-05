package com.SolucionesInformaticasBA.minimarket.modules.categorias.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.entity.Categoria;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.repository.CategoriaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CategoriaService implements CategoriasApi {
    private final CategoriaRepository categoriaRepository;
    // El repositorio de productos y no ProductosApi: ProductoService ya depende de esta API,
    // así que la dependencia inversa entre servicios cerraría un ciclo de beans.
    private final ProductoRepository productoRepository;

    @Override
    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = normalizar(request.getNombre());

        if (categoriaRepository.findByNombreAndDeletedAtIsNull(nombre).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre");
        }

        Categoria categoria = Categoria.builder()
            .nombre(nombre)
            .descripcion(normalizar(request.getDescripcion()))
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
    public Page<CategoriaResponse> getAll(Pageable pageable) {
        return categoriaRepository.findAllByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public CategoriaResponse update(UUID id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));

        String nombre = normalizar(request.getNombre());

        if (!nombre.equals(categoria.getNombre())
                && categoriaRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            throw new BadRequestException("Ya existe una categoría con ese nombre");
        }

        categoria.setNombre(nombre);
        categoria.setDescripcion(normalizar(request.getDescripcion()));
        return toResponse(categoriaRepository.save(categoria));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Categoria categoria = categoriaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));

        // Los productos guardan la categoría por id y sin FK lógica: si la damos de baja con
        // productos apuntándole, el catálogo empieza a devolverlos con categoria en null y no
        // queda registro de cuál era. Primero hay que reasignarlos.
        if (productoRepository.existsByIdCategoriaAndDeletedAtIsNull(id)) {
            throw new BadRequestException(
                "No se puede borrar una categoría con productos asignados. Reasignalos antes de darla de baja");
        }

        categoria.setDeletedAt(LocalDateTime.now());
        categoriaRepository.save(categoria);
    }

    @Override
    public boolean existsById(UUID id) {
        return categoriaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    /**
     * Sin recortar, " Bebidas" y "Bebidas" conviven: pasan el chequeo de duplicados del
     * servicio y también el índice único, porque la colación de MySQL 8 no ignora los
     * espacios. Quedaban dos categorías que en pantalla se ven idénticas.
     */
    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private CategoriaResponse toResponse(Categoria c) {
        return CategoriaResponse.builder()
            .id(c.getId())
            .nombre(c.getNombre())
            .descripcion(c.getDescripcion())
            .build();
    }
}
