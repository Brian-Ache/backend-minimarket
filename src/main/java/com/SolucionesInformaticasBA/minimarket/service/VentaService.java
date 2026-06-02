package com.SolucionesInformaticasBA.minimarket.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleVentaProductoManualRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleVentaProductoSistemaRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleVentaRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.request.VentaRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.DetalleVentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.VentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.mapper.VentaMapper;
import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.model.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.model.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.repository.VentaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaMapper ventaMapper;

    @Transactional
    public VentaResponseDTO realizarVenta(VentaRequestDTO request, Long usuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Venta venta = new Venta();
        venta.setUsuario(usuario);
        venta.setFecha(LocalDateTime.now());

        List<DetalleVenta> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (DetalleVentaRequestDTO d : request.getDetalles()) {

            // 🔥 validación básica
            if (d.getCantidad() == null || d.getCantidad() <= 0) {
                throw new RuntimeException("Cantidad inválida");
            }

            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);

            BigDecimal precio;

            // =========================
            // 🟢 PRODUCTO DEL SISTEMA
            // =========================
            if (d instanceof DetalleVentaProductoSistemaRequestDTO p) {

                Producto producto = productoRepository.findById(p.getProductoId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

                int stockActual = producto.getStock();
                int cantidadVendida = d.getCantidad();

                int cantidadADescontar = Math.min(stockActual, cantidadVendida);

                // ➖ stock (nunca negativo)
                producto.setStock(stockActual - cantidadADescontar);
                productoRepository.save(producto);

                // 💰 precio desde BD
                precio = producto.getPrecio();

                detalle.setProducto(producto);
                detalle.setNombreManual(null);

                // 📦 movimiento stock
                if (cantidadADescontar > 0) {
                    MovimientoStock movimiento = new MovimientoStock();
                    movimiento.setProducto(producto);
                    movimiento.setCantidad(-cantidadADescontar);
                    movimiento.setTipo(TipoMovimiento.VENTA);
                    movimiento.setMotivo("Venta realizada");
                    movimiento.setUsuario(usuario);

                    movimientoStockRepository.save(movimiento);
                }
            }

            // =========================
            // 🟡 PRODUCTO MANUAL
            // =========================
            else if (d instanceof DetalleVentaProductoManualRequestDTO m) {

                if (m.getNombreManual() == null || m.getNombreManual().isBlank()) {
                    throw new RuntimeException("Nombre manual requerido");
                }

                if (m.getPrecioUnitario() == null) {
                    throw new RuntimeException("Precio requerido para producto manual");
                }

                precio = m.getPrecioUnitario();

                detalle.setProducto(null);
                detalle.setNombreManual(m.getNombreManual());
            }

            else {
                throw new RuntimeException("Tipo de detalle inválido");
            }

            // =========================
            // 🧩 CAMPOS COMUNES
            // =========================
            detalle.setCantidad(d.getCantidad());
            detalle.setPrecioUnitario(precio);

            detalles.add(detalle);

            // 💰 subtotal y total
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(d.getCantidad()));
            total = total.add(subtotal);
        }

        // =========================
        // 💾 GUARDADO
        // =========================
        venta.setDetalles(detalles);
        venta.setTotal(total);

        Venta ventaGuardada = ventaRepository.save(venta);

        // =========================
        // 🔁 RESPONSE
        // =========================
        return mapToResponse(ventaGuardada);
    }

    // =========================
    // 🔁 MAPPER SIMPLE INTERNO
    // =========================
    private VentaResponseDTO mapToResponse(Venta venta) {

        VentaResponseDTO dto = new VentaResponseDTO();
        dto.setId(venta.getId());
        dto.setFecha(venta.getFecha());
        dto.setTotal(venta.getTotal());

        List<DetalleVentaResponseDTO> detallesDTO = new ArrayList<>();

        for (DetalleVenta d : venta.getDetalles()) {

            DetalleVentaResponseDTO det = new DetalleVentaResponseDTO();

            if (d.getProducto() != null) {
                det.setProductoId(d.getProducto().getId());
                det.setNombre(d.getProducto().getNombre());
                det.setTipo("PRODUCTO");
            } else {
                det.setNombre(d.getNombreManual());
                det.setTipo("MANUAL");
            }

            det.setCantidad(d.getCantidad());
            det.setPrecioUnitario(d.getPrecioUnitario());

            det.setSubtotal(
                d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()))
            );

            detallesDTO.add(det);
        }

        dto.setDetalles(detallesDTO);

        return dto;
    }
    
    // Método para obtener una venta por ID
    public VentaResponseDTO getById(Long id) {

    Venta venta = ventaRepository.findByIdConDetalles(id)
            .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

    return mapToResponse(venta);
    }

    // Método para obtener todas las ventas
    public List<VentaResponseDTO> getAll() {
    return ventaRepository.findAllConDetalles()
            .stream()
            .map(this::mapToResponse)
            .toList();
    }
}