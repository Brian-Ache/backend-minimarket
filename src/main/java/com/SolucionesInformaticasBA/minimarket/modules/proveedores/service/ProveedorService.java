package com.SolucionesInformaticasBA.minimarket.modules.proveedores.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.entity.Proveedor;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.repository.ProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
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
        String nombre = normalizar(request.getNombre());
        exigirNombreLibre(nombre);

        Proveedor proveedor = Proveedor.builder()
            .nombre(nombre)
            .telefono(normalizar(request.getTelefono()))
            .email(normalizar(request.getEmail()))
            .direccion(normalizar(request.getDireccion()))
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
    public ProveedorResponse getByIdIncluyendoBajas(UUID id) {
        Proveedor proveedor = proveedorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        return toResponse(proveedor);
    }

    @Override
    public Page<ProveedorResponse> getAll(boolean incluirBajas, Pageable pageable) {
        Page<Proveedor> proveedores = incluirBajas
            ? proveedorRepository.findAll(pageable)
            : proveedorRepository.findAllByDeletedAtIsNull(pageable);

        return proveedores.map(this::toResponse);
    }

    @Override
    @Transactional
    public ProveedorResponse update(UUID id, ProveedorRequest request) {
        Proveedor proveedor = proveedorRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));

        String nombre = normalizar(request.getNombre());
        if (!nombre.equals(proveedor.getNombre())) {
            exigirNombreLibre(nombre);
        }

        proveedor.setNombre(nombre);
        proveedor.setTelefono(normalizar(request.getTelefono()));
        proveedor.setEmail(normalizar(request.getEmail()));
        proveedor.setDireccion(normalizar(request.getDireccion()));
        return toResponse(proveedorRepository.save(proveedor));
    }

    /**
     * Baja lógica y nada más: las compras ya registradas lo siguen mostrando y la fila queda
     * disponible para {@link #restaurar}. Por eso no se valida que no haya productos ni
     * compras apuntándole, a diferencia de las categorías: acá la referencia vieja es
     * justamente lo que hay que conservar. Lo que sí queda cerrado es operar con él, porque
     * compras y el alta de productos validan con {@link #existsById}, que ignora las bajas.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Proveedor proveedor = proveedorRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        proveedor.setDeletedAt(LocalDateTime.now());
        proveedorRepository.save(proveedor);
    }

    /**
     * Revive la fila original en vez de dar de alta otra: las compras la referencian por id,
     * así que un alta nueva con los mismos datos partiría el historial en dos.
     */
    @Override
    @Transactional
    public ProveedorResponse restaurar(UUID id) {
        Proveedor proveedor = proveedorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));

        if (proveedor.getDeletedAt() == null) {
            throw new BadRequestException("El proveedor no está dado de baja");
        }

        // Mientras estuvo de baja su nombre quedó libre, así que alguien pudo haber dado de
        // alta otro proveedor con el mismo. Volver ahora dejaría dos activos indistinguibles.
        exigirNombreLibre(proveedor.getNombre());

        proveedor.setDeletedAt(null);
        return toResponse(proveedorRepository.save(proveedor));
    }

    @Override
    public boolean existsById(UUID id) {
        return proveedorRepository.existsByIdAndDeletedAtIsNull(id);
    }

    // Helpers

    /**
     * No hay índice único en la tabla —un proveedor dado de baja tiene que poder volver, y su
     * nombre no puede quedar reservado mientras tanto—, así que la unicidad entre los activos
     * se sostiene acá. Sin esto se podían cargar dos proveedores con el mismo nombre y no
     * había forma de distinguirlos al asignarlos a un producto o a una compra.
     */
    private void exigirNombreLibre(String nombre) {
        if (proveedorRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            throw new BadRequestException("Ya existe un proveedor con ese nombre");
        }
    }

    /** Sin recortar, " Distribuidora" y "Distribuidora" pasan como dos proveedores distintos. */
    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return ProveedorResponse.builder()
            .id(p.getId())
            .nombre(p.getNombre())
            .telefono(p.getTelefono())
            .email(p.getEmail())
            .direccion(p.getDireccion())
            .deletedAt(p.getDeletedAt())
            .build();
    }
}
