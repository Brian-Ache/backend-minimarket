package com.SolucionesInformaticasBA.minimarket.modules.caja.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movimientos_caja")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoCaja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_sesion", nullable = false)
    private UUID idSesion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoCaja tipo;

    @Column(nullable = false)
    private float monto;

    @Column(length = 255)
    private String motivo;

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    @Column(length = 20)
    private String origen;

    @Column(name = "id_referencia")
    private UUID idReferencia;

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
