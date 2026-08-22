package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
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
    private final CategoriasApi categoriasApi;
    private final ProveedoresApi proveedoresApi;

    @Transactional
    public ProductoResponse crear(UUID idUsuario, ProductoRequest request){
        if(!usuarioApi.existById(idUsuario)) {
            throw new RuntimeException("Usuario no encontrado");
        }

        if(productoRepository.findByBarcodeAndDeletedAtIsNull(request.getBarcode()) != null){
            throw new BadRequestException("Ya existe un producto con ese barcode");
        }

        validarCategoriaYProveedor(request.getIdCategoria(), request.getIdProveedor());

        Producto producto = toEntity(request);
        Producto guardado = productoRepository.save(producto);

        return toResponse(guardado);
    }

     public boolean existsById(UUID id){
        return productoRepository.existsByIdAndDeletedAtIsNull(id);
     }

    public ProductoResponse getById(UUID id){
        Producto producto = productoRepository.findByIdAndDeletedAtIsNull(id);
        if (producto == null) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        return toResponse(producto);
    }

    public Page<ProductoResponse> getAll(Pageable pageable){
        return productoRepository.findAllPaginated(pageable).map(this::toResponse);
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

        validarCategoriaYProveedor(request.getIdCategoria(), request.getIdProveedor());

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
        if(request.getCosto() != null && !request.getCosto().equals(producto.getCosto())){
            producto.setCosto(request.getCosto());
        }
        if(request.getMargen() != null && !request.getMargen().equals(producto.getMargen())){
            producto.setMargen(request.getMargen());
        }
        if(request.getIdCategoria() != null && !request.getIdCategoria().equals(producto.getIdCategoria())){
            producto.setIdCategoria(request.getIdCategoria());
        }
        if(request.getIdProveedor() != null && !request.getIdProveedor().equals(producto.getIdProveedor())){
            producto.setIdProveedor(request.getIdProveedor());
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

    @Override
    public Page<ProductoResponse> search(String q, Pageable pageable) {
        return productoRepository.findByNombreContainingIgnoreCase(q, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndCategoria(String q, UUID idCategoria, Pageable pageable) {
        return productoRepository.searchByNombreAndCategoria(q, idCategoria, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndProveedor(String q, UUID idProveedor, Pageable pageable) {
        return productoRepository.searchByNombreAndProveedor(q, idProveedor, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndCategoriaAndProveedor(String q, UUID idCategoria, UUID idProveedor, Pageable pageable) {
        return productoRepository.searchByNombreAndCategoriaAndProveedor(q, idCategoria, idProveedor, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> getByCategoria(UUID idCategoria, Pageable pageable) {
        return productoRepository.findByIdCategoriaAndDeletedAtIsNull(idCategoria, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> getByProveedor(UUID idProveedor, Pageable pageable) {
        return productoRepository.findByIdProveedorAndDeletedAtIsNull(idProveedor, pageable)
            .map(this::toResponse);
    }

    @Override
    public Page<ProductoResponse> getByCategoriaAndProveedor(UUID idCategoria, UUID idProveedor, Pageable pageable) {
        return productoRepository.findByIdCategoriaAndIdProveedorAndDeletedAtIsNull(idCategoria, idProveedor, pageable)
            .map(this::toResponse);
    }

    // Helpers

    private void validarCategoriaYProveedor(UUID idCategoria, UUID idProveedor) {
        if (idCategoria != null && !categoriasApi.existsById(idCategoria)) {
            throw new BadRequestException("La categoría especificada no existe");
        }
        if (idProveedor != null && !proveedoresApi.existsById(idProveedor)) {
            throw new BadRequestException("El proveedor especificado no existe");
        }
    }

    private Producto toEntity(ProductoRequest request){
        return Producto.builder()
            .nombre(request.getNombre())
            .barcode(request.getBarcode())
            .precio(request.getPrecio())
            .manejaLotes(request.isManejaLotes())
            .costo(request.getCosto())
            .margen(request.getMargen())
            .idCategoria(request.getIdCategoria())
            .idProveedor(request.getIdProveedor())
            .build();
    }

    private ProductoResponse toResponse(Producto p) {
        CategoriaResponse categoria = null;
        if (p.getIdCategoria() != null) {
            try {
                categoria = categoriasApi.getById(p.getIdCategoria());
            } catch (ResourceNotFoundException e) {
                categoria = null;
            }
        }

        ProveedorResponse proveedor = null;
        if (p.getIdProveedor() != null) {
            try {
                proveedor = proveedoresApi.getById(p.getIdProveedor());
            } catch (ResourceNotFoundException e) {
                proveedor = null;
            }
        }

        return ProductoResponse.builder()
            .id(p.getId())
            .nombre(p.getNombre())
            .barcode(p.getBarcode())
            .precio(p.getPrecio())
            .manejaLotes(p.isManejaLotes())
            .costo(p.getCosto())
            .margen(p.getMargen())
            .categoria(categoria)
            .proveedor(proveedor)
            .build();
    }
}
