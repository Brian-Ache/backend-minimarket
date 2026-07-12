package com.SolucionesInformaticasBA.minimarket.modules.proveedores.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.entity.Proveedor;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.repository.ProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProveedorService implements ProveedoresApi {
    private final ProveedorRepository proveedorRepository;

    @Override
    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        Proveedor proveedor = Proveedor.builder()
            .nombre(request.getNombre())
            .telefono(request.getTelefono())
            .email(request.getEmail())
            .direccion(request.getDireccion())
            .build();
        return toResponse(proveedorRepository.save(proveedor));
    }

    @Override
    public ProveedorResponse getById(UUID id) {
        Proveedor proveedor = proveedorRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        return toResponse(proveedor);
    }

    @Override
    public List<ProveedorResponse> getAll() {
        return proveedorRepository.findAll().stream()
            .filter(p -> p.getDeletedAt() == null)
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public ProveedorResponse update(UUID id, ProveedorRequest request) {
        Proveedor proveedor = proveedorRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));

        proveedor.setNombre(request.getNombre());
        proveedor.setTelefono(request.getTelefono());
        proveedor.setEmail(request.getEmail());
        proveedor.setDireccion(request.getDireccion());
        return toResponse(proveedorRepository.save(proveedor));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Proveedor proveedor = proveedorRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        proveedor.setDeletedAt(LocalDateTime.now());
        proveedorRepository.save(proveedor);
    }

    @Override
    public boolean existsById(UUID id) {
        return proveedorRepository.existsByIdAndDeletedAtIsNull(id);
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return ProveedorResponse.builder()
            .id(p.getId())
            .nombre(p.getNombre())
            .telefono(p.getTelefono())
            .email(p.getEmail())
            .direccion(p.getDireccion())
            .build();
    }
}
