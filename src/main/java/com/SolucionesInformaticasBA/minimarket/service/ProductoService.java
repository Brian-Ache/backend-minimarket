package com.SolucionesInformaticasBA.minimarket.service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.SolucionesInformaticasBA.minimarket.dto.request.ProductoRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.ProductoResponseDTO;
import com.SolucionesInformaticasBA.minimarket.mapper.ProductoMapper;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    private final ProductoMapper productoMapper;

    // 🟢 CREAR
    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO request, Long usuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ❗ validar barcode único
        if (productoRepository.findByBarcode(request.getBarcode()).isPresent()) {
            throw new RuntimeException("Ya existe un producto con ese barcode");
        }

        Producto producto = productoMapper.toEntity(request, usuario);

        Producto guardado = productoRepository.save(producto);

        return productoMapper.toDTO(guardado);
    }

    // 🔍 GET BY ID
    public ProductoResponseDTO getById(Long id) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        return productoMapper.toDTO(producto);
    }

    // 📋 GET ALL
    public List<ProductoResponseDTO> getAll() {

        return productoRepository.findAll()
                .stream()
                .map(productoMapper::toDTO)
                .toList();
    }

    // 🔎 GET BY BARCODE
    public ProductoResponseDTO getByBarcode(String barcode) {

        Producto producto = productoRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        return productoMapper.toDTO(producto);
    }

    // ✏️ UPDATE
    @Transactional
    public ProductoResponseDTO update(Long id, ProductoRequestDTO request) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        // validar barcode único (si cambia)
        if (!producto.getBarcode().equals(request.getBarcode())) {
            if (productoRepository.findByBarcode(request.getBarcode()).isPresent()) {
                throw new RuntimeException("Ya existe un producto con ese barcode");
            }
        }

        producto.setNombre(request.getNombre());
        producto.setBarcode(request.getBarcode());
        producto.setManejaLotes(request.getManejaLotes());

        producto.setFechaUpdate(LocalDateTime.now());

        Producto actualizado = productoRepository.save(producto);

        return productoMapper.toDTO(actualizado);
    }

    // ❌ DELETE (soft delete)
    @Transactional
    public void delete(Long id) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setFechaEliminacion(LocalDateTime.now());

        productoRepository.save(producto);
    }
}