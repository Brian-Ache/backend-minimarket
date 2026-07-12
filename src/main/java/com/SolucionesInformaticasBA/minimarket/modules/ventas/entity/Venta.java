package com.SolucionesInformaticasBA.minimarket.modules.ventas.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;

@Entity
@Table(name = "ventas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id; // mas seguro que id incrementales

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    private float total;

    @Builder.Default
    @Column(nullable = true)
    private Boolean cobrada = false;

    @Column(name = "fecha_cobro", nullable = true)
    private LocalDateTime fechaCobro;

    @Column(name = "metodo_pago", nullable = true, length = 20)
    private String metodoPago;

    @Column(name = "monto_recibido", nullable = true)
    private Float montoRecibido;

    @Column(name = "id_sesion", nullable = true)
    private UUID idSesion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;
}