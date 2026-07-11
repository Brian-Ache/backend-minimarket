package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductoService implements ProductosApi{
    private final ProductoRepository productoRepository;
    private final UsuarioApi usuarioApi;

    @Transactional
    public ProductoResponse crear(UUID idUsuario, ProductoRequest request){
        if(!usuarioApi.existById(idUsuario)) {
            throw new RuntimeException("Usuario no encontrado");
        }

        // valido barcode unico (helper despues ??)
        if(productoRepository.findByBarcodeAndDeletedAtIsNull(request.getBarcode()) != null){
            throw new BadRequestException("Ya existe un producto con ese barcode");
        }

        Producto producto = toEntity(request);
        Producto guardado = productoRepository.save(producto);

        return toResponse(guardado);
    }

     public boolean existsById(UUID id){
        return productoRepository.existsByIdAndDeletedAtIsNull(id);
     }

    public ProductoResponse getById(UUID id){
        return toResponse(productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado")));
    }

    public List<ProductoResponse> getAll(){
        return productoRepository.findAllByDeletedAtIsNull()
            .stream().map(this::toResponse).toList();
    }

    public ProductoResponse getByBarcode(String barcode){
        Producto producto = productoRepository.findByBarcodeAndDeletedAtIsNull(barcode);
        if(producto == null){
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        return toResponse(producto);
    }

    @Transactional
    public ProductoResponse update(UUID idProducto, ProductoRequest request){
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if(!producto.getBarcode().equals(request.getBarcode())
                && productoRepository.findByBarcodeAndDeletedAtIsNull(request.getBarcode()) != null){
            throw new BadRequestException("Ya existe un producto con ese barcode");
        }

        if(!producto.getNombre().equals(request.getNombre())){
            producto.setNombre(request.getNombre());
        }
        if(!producto.getBarcode().equals(request.getBarcode())){
            producto.setBarcode(request.getBarcode());
        }
        if(producto.getPrecio() != request.getPrecio()){
            producto.setPrecio(request.getPrecio());
        }
        if(producto.isManejaLotes() != request.isManejaLotes()){
            producto.setManejaLotes(request.isManejaLotes());
        }

        Producto actualizado = productoRepository.save(producto);
        return toResponse(actualizado);
    }

    @Transactional
    public void delete(UUID id){
        Producto p = productoRepository.findByIdAndDeletedAtIsNull(id);
        if(p == null) throw new ResourceNotFoundException("Producto no encontrado");
        
        p.setDeletedAt(LocalDateTime.now());
        productoRepository.save(p);
    }

    // Helpers
    private Producto toEntity(ProductoRequest request){
        return Producto.builder().nombre(request.getNombre())
            .barcode(request.getBarcode())
            .precio(request.getPrecio())
            .manejaLotes(request.isManejaLotes())
            .build();
    }

    private ProductoResponse toResponse(Producto p){
        return ProductoResponse.builder().id(p.getId())
            .nombre(p.getNombre())
            .barcode(p.getBarcode())
            .precio(p.getPrecio())
            .manejaLotes(p.isManejaLotes())
            .build();
    }
}
