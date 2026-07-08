package com.SolucionesInformaticasBA.minimarket.service;

import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.repository.UsuarioRepository;

import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.dto.request.LoteRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.LoteResponseDTO;
import com.SolucionesInformaticasBA.minimarket.mapper.LoteMapper;
import com.SolucionesInformaticasBA.minimarket.model.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.Entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.repository.LoteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository loteRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioApi usuarioApi;

    private final LoteMapper loteMapper;

    @Transactional
    public LoteResponseDTO crearLote(LoteRequestDTO request, UUID usuarioId) {

        Usuario u = usuarioApi.getUsuarioById(usuarioId); // Respeto contrato del modulo y principio de responsabilidad unica


        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (request.getFechaVencimiento() == null) {
            throw new RuntimeException("Fecha de vencimiento obligatoria");
        }

        Lote lote = loteMapper.toEntity(request, producto, u);

        Lote guardado = loteRepository.save(lote);

        return loteMapper.toDTO(guardado);
    }

    // 🔍 listar todos
    public List<LoteResponseDTO> getAll() {
        return loteRepository.findAll()
                .stream()
                .map(loteMapper::toDTO)
                .toList();
    }

    // ⚠️ próximos a vencer
    public List<LoteResponseDTO> proximosAVencer(int dias) {
    LocalDate hoy = LocalDate.now();
    LocalDate limite = hoy.plusDays(dias);

    return loteRepository
            // Buscamos entre HOY y el LIMITE (excluye lo ya vencido)
            .findByFechaVencimientoBetweenAndFechaEliminacionIsNull(hoy, limite)
            .stream()
            .map(loteMapper::toDTO)
            .toList();
    }

    public List<LoteResponseDTO> vencidos(int dias) {

        LocalDate limite = LocalDate.now().minusDays(dias);

        return loteRepository
                .findByFechaVencimientoBeforeAndFechaEliminacionIsNull(limite)
                .stream()
                .map(loteMapper::toDTO)
                .toList();
    }

    public List<LoteResponseDTO> vigentes() {

        LocalDate hoy = LocalDate.now();

        return loteRepository
                .findByFechaVencimientoAfterAndFechaEliminacionIsNull(hoy)
                .stream()
                .map(loteMapper::toDTO)
                .toList();
    }
}