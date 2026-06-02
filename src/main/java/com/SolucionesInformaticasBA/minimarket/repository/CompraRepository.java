package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;


public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Compra> findByUsuarioId(Long usuarioId);
}