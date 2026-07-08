package com.SolucionesInformaticasBA.minimarket.model.entity;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;

import jakarta.persistence.*;


@Entity
@Table(name = "movimiento_stock")
@Data
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id") // id_entidad solo para FK
    private UUID id; // mas seguro para no exponer id incremementales si no es necesario

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto; // revisar si se necesita que sea incremental o cambiar a UUID

    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;

    private String motivo;

    @Column(name = "id_usuario") // se usa join column cuando hay herencia, sino es sobrecomplejuzarlo
    private UUID idUsuario;

    private LocalDateTime fecha; // estandarisas los 3 timestamps para auditorias

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }
}