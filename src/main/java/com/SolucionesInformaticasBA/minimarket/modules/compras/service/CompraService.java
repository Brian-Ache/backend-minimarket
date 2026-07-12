package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.DetalleCompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.DetalleCompra;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.CompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.DetalleCompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CompraService implements CompraApi{
    private final CompraRepository compraRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    private final UsuarioApi usuarioApi;
    private final ProductosApi productosApi;

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

    // Helpers

    public CompraResponseDTO toDTO(Compra compra) {

        CompraResponseDTO dto = new CompraResponseDTO();

        dto.setId(compra.getId());
        dto.setFecha(compra.getCreatedAt());
        dto.setTotal(compra.getTotal());

        DetalleCompra d = 

        List<DetalleCompraResponseDTO> detalle = 

        // buscar por id de compra y armar la lista, compra no deberia llevar detalles (dependencia doble)
        List<DetalleCompraResponseDTO> detalles = compra.getDetalles()
                .stream()
                .map(detalleCompraMapper::toDTO)
                .toList();

        dto.setDetalles(detalles);

        return dto;
    }

    public DetalleCompra toEntity(
            DetalleCompraRequestDTO dto,
            Producto producto,
            Compra compra
    ) {
        DetalleCompra d = new DetalleCompra();

        d.setIdProducto(producto.getId());
        d.setIdCompra(compra.getId());
        d.setCantidad(dto.getCantidad());
        d.setPrecioUnitario(dto.getPrecioUnitario());

        return d;
    }

    public DetalleCompraResponseDTO toDTO(DetalleCompra d) {
        Optional<Producto> p = productoRepository.findById(d.getIdProducto());
        DetalleCompraResponseDTO dto = new DetalleCompraResponseDTO();

        dto.setIdProducto(d.getIdProducto());
        dto.setNombreProducto(p.getNombre());
        dto.setCantidad(d.getCantidad());
        dto.setPrecioUnitario(d.getPrecioUnitario());

        return dto;
    }

}
