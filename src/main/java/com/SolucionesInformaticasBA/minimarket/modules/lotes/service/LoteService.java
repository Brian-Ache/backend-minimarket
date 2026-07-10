package com.SolucionesInformaticasBA.minimarket.modules.lotes.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.LoteApi;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class LoteService implements LoteApi {
    private final LoteRepository loteRepository;
    private final ProductosApi productosApi;

    @Transactional
    public LoteResponse crear(UUID idUsuario, LoteRequest request){
        if(request.getFechaVencimiento() == null) throw new RuntimeException("Fecha de vencimiento obligatoria");

        Lote lote = toEntity(request, idUsuario);
        Lote guardado = loteRepository.save(lote);

        return toResponse(guardado);
    }

    public List<LoteResponse> getAll(){
        return loteRepository.findAllByDeletedAtIsNull().stream()
            .map(this::actualizarEstado)
            .map(this::toResponse)
            .toList();
    }

    public List<LoteResponse> getByEstado(String estado) {
        return getAll().stream()
            .filter(lote -> lote.getEstado().equals(EstadoLote.valueOf(estado.toUpperCase()).name()))
            .toList();
    }

    // Helpers

    private EstadoLote calcularEstado(LocalDate fechaVencimiento){
        LocalDate hoy = LocalDate.now();

        if(fechaVencimiento == null) return EstadoLote.SIN_FECHA;
        if(fechaVencimiento.isBefore(hoy)) return EstadoLote.VENCIDO;
        if(fechaVencimiento.isBefore(hoy.plusDays(7))) return EstadoLote.PROXIMO;

        return EstadoLote.VIGENTE;
    }

    private Lote actualizarEstado(Lote lote){
        EstadoLote actual = lote.getEstado();
        EstadoLote nuevo = calcularEstado(lote.getFechaVencimiento());

        if (actual != nuevo){
            lote.setEstado(nuevo);
            loteRepository.save(lote);
        }

        return lote;
    }

    private Lote toEntity(LoteRequest request, UUID idCreador){
        return Lote.builder()
            .idProducto(request.getIdProducto())
            .numeroLote(request.getNumeroLote())
            .estado(calcularEstado(request.getFechaVencimiento()))
            .fechaVencimiento(request.getFechaVencimiento())
            .cantidad(request.getCantidad())
            .idUsuarioCreador(idCreador)
            .build();
    }

    private LoteResponse toResponse(Lote l){
        return LoteResponse.builder()
            .id(l.getId())
            .idProducto(l.getIdProducto())
            .nombreProducto(productosApi.getProductoById(l.getIdProducto()).getNombre())
            .numeroLote(l.getNumeroLote())
            .fechaVencimiento(l.getFechaVencimiento())
            .cantidad(l.getCantidad())
            .estado(l.getEstado().name())
            .build();
    }
}
