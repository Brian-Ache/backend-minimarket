package com.SolucionesInformaticasBA.minimarket.service;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.dto.request.AjusteStockRequestDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.repository.UsuarioRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AjusteStockService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void ajustarStock(AjusteStockRequestDTO request, Long usuarioId) {

        // 👤 Usuario
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 📦 Producto
        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        // ⚠️ Validaciones
        if (request.getStockReal() == null || request.getStockReal() < 0) {
            throw new RuntimeException("Stock inválido");
        }

        int stockActual = producto.getStock();
        int stockReal = request.getStockReal();

        // 🔢 Diferencia
        int diferencia = stockReal - stockActual;

        if (diferencia == 0) {
            return; // no hay cambios
        }

        // 🧠 Actualizar stock (nunca negativo)
        producto.setStock(stockReal);
        productoRepository.save(producto);

        // 📦 Registrar movimiento
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setProducto(producto);
        movimiento.setCantidad(diferencia); // puede ser + o -
        movimiento.setTipo(TipoMovimiento.AJUSTE);
        movimiento.setMotivo(
                request.getMotivo() != null
                        ? request.getMotivo()
                        : "Ajuste manual de stock"
        );
        movimiento.setUsuario(usuario);

        movimientoStockRepository.save(movimiento);
    }
}