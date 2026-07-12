package com.SolucionesInformaticasBA.minimarket.modules.compras.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "compras")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Compra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_usuario")
    private UUID idUsuario;

    private float total;

    @Column(name = "id_proveedor", nullable = true)
    private UUID idProveedor;

    @Column(name = "tipo_comprobante", nullable = true, length = 20)
    private String tipoComprobante;

    @Column(name = "nro_comprobante", nullable = true, length = 50)
    private String nroComprobante;

    @Column(nullable = true, length = 255)
    private String observaciones;

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
