package com.SolucionesInformaticasBA.minimarket.model.entity;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;

import jakarta.persistence.*;


@Entity
@Table(name = "movimiento_stock")
@Data
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;

    private String motivo;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fecha;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }
}