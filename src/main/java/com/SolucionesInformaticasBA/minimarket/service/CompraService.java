package com.SolucionesInformaticasBA.minimarket.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.dto.request.CompraRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleCompraRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.CompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.VentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.mapper.CompraMapper;
import com.SolucionesInformaticasBA.minimarket.mapper.DetalleCompraMapper;
import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleCompra;
import com.SolucionesInformaticasBA.minimarket.model.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.Entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.repository.CompraRepository;
import com.SolucionesInformaticasBA.minimarket.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompraService {

    private final ProductoRepository productoRepository;
    private final CompraRepository compraRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioApi usuarioApi;

    private final DetalleCompraMapper detalleCompraMapper;
    private final CompraMapper compraMapper;

    @Transactional
    public CompraResponseDTO registrarCompra(CompraRequestDTO request, UUID usuarioId) {

        // 🔍 Usuario
        Usuario usuario = usuarioApi.getUsuarioById(usuarioId); // Respeto contrato del modulo y principio de responsabilidad unica



            producto.setStock(producto.getStock() + d.getCantidad());
            productoRepository.save(producto);

            // 🧩 Crear detalle
            DetalleCompra detalle = detalleCompraMapper.toEntity(d, producto, compra);
            detalles.add(detalle);

            // 💰 Calcular subtotal
            float subtotal = d.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(d.getCantidad()));

            total = total.add(subtotal);

            // 📦 Movimiento de stock
            MovimientoStock movimiento = new MovimientoStock();
            movimiento.setIdProducto(producto.getId());
            movimiento.setCantidad(d.getCantidad());
            movimiento.setTipo(TipoMovimiento.COMPRA);
            movimiento.setMotivo("Ingreso de mercadería");
            movimiento.setIdUsuario(usuario.getId());

            movimientoStockRepository.save(movimiento);
        }

        // 🧾 Setear compra
        compra.setDetalles(detalles);
        compra.setTotal(total);

        // 💾 Guardar compra
        Compra compraGuardada = compraRepository.save(compra);

        return compraMapper.toDTO(compraGuardada);
    }


    // Método para obtener todas las compras
    public List<CompraResponseDTO> getAll() {

        return compraRepository.findAll()
                .stream()
                .map(compraMapper::toDTO)
                .toList();
    }


    // Método para obtener una venta por ID
    public CompraResponseDTO getById(Long id) {

        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        return compraMapper.toDTO(compra);
    }
}