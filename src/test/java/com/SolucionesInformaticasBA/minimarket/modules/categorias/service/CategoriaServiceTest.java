package com.SolucionesInformaticasBA.minimarket.modules.categorias.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.entity.Categoria;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.repository.CategoriaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProductoRepository productoRepository;

    @InjectMocks private CategoriaService categoriaService;

    private static final UUID ID_CATEGORIA = UUID.randomUUID();

    @Test
    @DisplayName("no se puede borrar una categoría con productos asignados")
    void deleteConProductosEsBadRequest() {
        when(categoriaRepository.findByIdAndDeletedAtIsNull(ID_CATEGORIA))
                .thenReturn(Optional.of(categoriaActiva()));
        when(productoRepository.existsByIdCategoriaAndDeletedAtIsNull(ID_CATEGORIA)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> categoriaService.delete(ID_CATEGORIA));

        assertEquals("No se puede borrar una categoría con productos asignados. "
                + "Reasignalos antes de darla de baja", ex.getMessage());
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("una categoría sin productos se da de baja")
    void deleteSinProductos() {
        when(categoriaRepository.findByIdAndDeletedAtIsNull(ID_CATEGORIA))
                .thenReturn(Optional.of(categoriaActiva()));
        when(productoRepository.existsByIdCategoriaAndDeletedAtIsNull(ID_CATEGORIA)).thenReturn(false);

        categoriaService.delete(ID_CATEGORIA);

        ArgumentCaptor<Categoria> captor = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    @DisplayName("el nombre de una categoría dada de baja se puede volver a usar")
    void seReutilizaElNombreDeUnaCategoriaBorrada() {
        // findByNombreAndDeletedAtIsNull filtra las bajas, así que la que quedó con ese
        // nombre no bloquea el alta. El índice único de la base acompaña desde la 06.
        when(categoriaRepository.findByNombreAndDeletedAtIsNull("Bebidas")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoriaResponse response = categoriaService.crear(request("Bebidas", null));

        assertEquals("Bebidas", response.getNombre());
    }

    @Test
    @DisplayName("no se admiten dos categorías activas con el mismo nombre")
    void crearDuplicadaEsBadRequest() {
        when(categoriaRepository.findByNombreAndDeletedAtIsNull("Bebidas"))
                .thenReturn(Optional.of(categoriaActiva()));

        assertThrows(BadRequestException.class, () -> categoriaService.crear(request("Bebidas", null)));

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("el nombre se recorta antes de compararlo y de guardarlo")
    void crearRecortaElNombre() {
        when(categoriaRepository.findByNombreAndDeletedAtIsNull("Bebidas")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoriaResponse response = categoriaService.crear(request("  Bebidas  ", "  Gaseosas y aguas  "));

        assertEquals("Bebidas", response.getNombre());
        assertEquals("Gaseosas y aguas", response.getDescripcion());
    }

    @Test
    @DisplayName("el listado no trae las categorías dadas de baja desde la base")
    void getAllConsultaSoloLasActivas() {
        Pageable pageable = PageRequest.of(0, 20);
        when(categoriaRepository.findAllByDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(categoriaActiva()), pageable, 1));

        assertEquals(1, categoriaService.getAll(pageable).getTotalElements());
        verify(categoriaRepository, never()).findAll(pageable);
    }

    private Categoria categoriaActiva() {
        return Categoria.builder()
                .id(ID_CATEGORIA)
                .nombre("Bebidas")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CategoriaRequest request(String nombre, String descripcion) {
        CategoriaRequest request = new CategoriaRequest();
        request.setNombre(nombre);
        request.setDescripcion(descripcion);
        return request;
    }
}
