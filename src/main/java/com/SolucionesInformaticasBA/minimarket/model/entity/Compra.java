package com.SolucionesInformaticasBA.minimarket.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "compra")
@Data
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // revisar si necesita ser incremental

    @Column(name = "id_usuario")
    private UUID idUsuario;

    private LocalDateTime fecha; // fecha o timestamp de creacion? pensar para auditar

    private BigDecimal total;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL)
    private List<DetalleCompra> detalles;
}