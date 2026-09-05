package com.SolucionesInformaticasBA.minimarket.modules.proveedores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.entity.Proveedor;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.repository.ProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

    @Mock private ProveedorRepository proveedorRepository;

    @InjectMocks private ProveedorService proveedorService;

    private static final UUID ID_PROVEEDOR = UUID.randomUUID();

    @Test
    @DisplayName("no se admiten dos proveedores activos con el mismo nombre")
    void crearDuplicadoEsBadRequest() {
        when(proveedorRepository.existsByNombreAndDeletedAtIsNull("Distribuidora Sur")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> proveedorService.crear(request("Distribuidora Sur")));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("el nombre se recorta antes de compararlo y de guardarlo")
    void crearRecortaElNombre() {
        when(proveedorRepository.existsByNombreAndDeletedAtIsNull("Distribuidora Sur")).thenReturn(false);
        when(proveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProveedorResponse response = proveedorService.crear(request("  Distribuidora Sur  "));

        assertEquals("Distribuidora Sur", response.getNombre());
    }

    @Test
    @DisplayName("dar de baja no exige que no tenga compras ni productos")
    void deleteSoloMarcaLaBaja() {
        when(proveedorRepository.findByIdAndDeletedAtIsNull(ID_PROVEEDOR))
                .thenReturn(Optional.of(proveedorActivo()));

        proveedorService.delete(ID_PROVEEDOR);

        ArgumentCaptor<Proveedor> captor = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedorRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    @DisplayName("el historial resuelve al proveedor dado de baja, con deletedAt cargado")
    void getByIdIncluyendoBajasDevuelveElDadoDeBaja() {
        when(proveedorRepository.findById(ID_PROVEEDOR)).thenReturn(Optional.of(proveedorDadoDeBaja()));

        ProveedorResponse response = proveedorService.getByIdIncluyendoBajas(ID_PROVEEDOR);

        assertEquals("Distribuidora Sur", response.getNombre());
        assertNotNull(response.getDeletedAt());
    }

    @Test
    @DisplayName("el getById normal sigue ocultando a los dados de baja")
    void getByIdNoDevuelveLosDadosDeBaja() {
        when(proveedorRepository.findByIdAndDeletedAtIsNull(ID_PROVEEDOR)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> proveedorService.getById(ID_PROVEEDOR));
    }

    @Test
    @DisplayName("existsById ignora las bajas: es lo que cierra las compras nuevas")
    void existsByIdIgnoraLasBajas() {
        when(proveedorRepository.existsByIdAndDeletedAtIsNull(ID_PROVEEDOR)).thenReturn(false);

        assertEquals(false, proveedorService.existsById(ID_PROVEEDOR));
    }

    @Test
    @DisplayName("el listado normal no trae las bajas y con incluirBajas sí")
    void getAllSegunIncluirBajas() {
        Pageable pageable = PageRequest.of(0, 20);

        when(proveedorRepository.findAllByDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(proveedorActivo()), pageable, 1));
        assertEquals(1, proveedorService.getAll(false, pageable).getTotalElements());

        when(proveedorRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(proveedorActivo(), proveedorDadoDeBaja()), pageable, 2));
        assertEquals(2, proveedorService.getAll(true, pageable).getTotalElements());
    }

    @Test
    @DisplayName("restaurar revive la fila original")
    void restaurarLimpiaLaBaja() {
        when(proveedorRepository.findById(ID_PROVEEDOR)).thenReturn(Optional.of(proveedorDadoDeBaja()));
        when(proveedorRepository.existsByNombreAndDeletedAtIsNull("Distribuidora Sur")).thenReturn(false);
        when(proveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProveedorResponse response = proveedorService.restaurar(ID_PROVEEDOR);

        assertNull(response.getDeletedAt());
        assertEquals(ID_PROVEEDOR, response.getId());
    }

    @Test
    @DisplayName("restaurar uno que está en pie es un error")
    void restaurarActivoEsBadRequest() {
        when(proveedorRepository.findById(ID_PROVEEDOR)).thenReturn(Optional.of(proveedorActivo()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> proveedorService.restaurar(ID_PROVEEDOR));

        assertEquals("El proveedor no está dado de baja", ex.getMessage());
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se restaura si mientras tanto alguien tomó el nombre")
    void restaurarConNombreTomadoEsBadRequest() {
        when(proveedorRepository.findById(ID_PROVEEDOR)).thenReturn(Optional.of(proveedorDadoDeBaja()));
        when(proveedorRepository.existsByNombreAndDeletedAtIsNull("Distribuidora Sur")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> proveedorService.restaurar(ID_PROVEEDOR));

        verify(proveedorRepository, never()).save(any());
    }

    private Proveedor proveedorActivo() {
        return Proveedor.builder()
                .id(ID_PROVEEDOR)
                .nombre("Distribuidora Sur")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Proveedor proveedorDadoDeBaja() {
        Proveedor p = proveedorActivo();
        p.setDeletedAt(LocalDateTime.now().minusDays(3));
        return p;
    }

    private ProveedorRequest request(String nombre) {
        ProveedorRequest request = new ProveedorRequest();
        request.setNombre(nombre);
        return request;
    }
}
