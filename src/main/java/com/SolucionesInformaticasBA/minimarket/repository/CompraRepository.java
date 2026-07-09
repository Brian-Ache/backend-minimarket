package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public interface CompraRepository extends JpaRepository<Compra, UUID> {

    List<Compra> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Compra> findByUsuarioId(Long usuarioId);
}