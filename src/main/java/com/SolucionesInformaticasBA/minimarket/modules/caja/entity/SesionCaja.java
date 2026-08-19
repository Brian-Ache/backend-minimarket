package com.SolucionesInformaticasBA.minimarket.modules.caja.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;

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
@Table(name = "sesiones_caja")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SesionCaja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "saldo_inicial", nullable = false)
    private float saldoInicial;

    @Column(name = "saldo_final")
    private Float saldoFinal;

    @Column(name = "saldo_esperado")
    private Float saldoEsperado;

    private Float diferencia;

    // Desglose del arqueo, congelado al cerrar. Un corte es un documento contable:
    // se guarda como quedó, no se recalcula al consultarlo.
    @Column(name = "total_ventas")
    private Float totalVentas;

    @Column(name = "cantidad_ventas")
    private Integer cantidadVentas;

    @Column(name = "total_compras")
    private Float totalCompras;

    @Column(name = "cantidad_compras")
    private Integer cantidadCompras;

    @Column(name = "total_entradas_manuales")
    private Float totalEntradasManuales;

    @Column(name = "total_salidas_manuales")
    private Float totalSalidasManuales;

    @Column(length = 255)
    private String observaciones;

    @Column(name = "id_usuario_apertura", nullable = false)
    private UUID idUsuarioApertura;

    @Column(name = "id_usuario_cierre")
    private UUID idUsuarioCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSesion estado;

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
